package com.example.tommymap.ui.main

import android.Manifest
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.setMargins
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.tommymap.BuildConfig
import com.example.tommymap.R
import com.example.tommymap.data.NavigationRepository
import com.example.tommymap.data.NavigationRepositoryImpl
import com.example.tommymap.data.SearchRepositoryImpl
import com.example.tommymap.data.TommyLocationProvider
import com.example.tommymap.dp2px
import com.example.tommymap.isLocationPermissionGranted
import com.example.tommymap.ui.search.SearchViewModel
import com.example.tommymap.ui.search.TommySearchView
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStore
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStoreConfiguration
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton
import com.tomtom.sdk.navigation.UnitSystemType
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.search.Search
import com.tomtom.sdk.search.online.OnlineSearch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale


class MainActivity : AppCompatActivity() {

    // dependencies
    private val locationProvider: TommyLocationProvider by lazy {
        TommyLocationProvider(this)
    }
    private val onlineSearch: Search by lazy {
        OnlineSearch.create(this, BuildConfig.TOMTOM_API_KEY)
    }
    private val routePlanner: RoutePlanner by lazy {
        OnlineRoutePlanner.create(this, BuildConfig.TOMTOM_API_KEY)
    }
    private val tileStore: NavigationTileStore by lazy {
        NavigationTileStore.create(this, NavigationTileStoreConfiguration(BuildConfig.TOMTOM_API_KEY))
    }
    private val tomTomNavigationProvider = lazy {
        OnlineTomTomNavigationFactory.create(Configuration(this, tileStore, locationProvider, routePlanner))
    }
    private val navigationRepository: NavigationRepository by lazy {
        NavigationRepositoryImpl(routePlanner)
    }
    // ----

    private lateinit var mainViewModel: MainViewModel
    private lateinit var searchView: View
    private lateinit var simulationButton: Button

    private val mapContainerId = View.generateViewId()

    private val mapFragment: MapFragment by lazy {
        MapFragment.newInstance(MapOptions(BuildConfig.TOMTOM_API_KEY))
    }

    private val navigationFragment: NavigationFragment by lazy {
        val navigationUiOptions = NavigationUiOptions(
            voiceLanguage = Locale.getDefault(),
            keepInBackground = true,
            isSoundEnabled = true,
            unitSystemType = UnitSystemType.default
        )
        NavigationFragment.newInstance(navigationUiOptions)
    }

    private val navigationListener = object : NavigationFragment.NavigationListener {
        override fun onStarted() {
            mainViewModel.onNavigationStarted(resources.getDimension(R.dimen.map_padding_bottom).toInt())
        }

        override fun onStopped() {
            mainViewModel.stopNavigation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val factory = MainViewModel.Factory(locationProvider, tomTomNavigationProvider, navigationRepository)
        mainViewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(frameLayout)

        frameLayout.addView(setupMapContainer())
        frameLayout.addView(setupSearchView())
        frameLayout.addView(setupSimulationButton())

        requestLocationPermission()
        configureViewModel()
    }

    override fun onDestroy() {
        locationProvider.close()
        onlineSearch.close()
        routePlanner.close()
        tileStore.close()
        if (tomTomNavigationProvider.isInitialized()) {
            tomTomNavigationProvider.value.close()
        }
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        hackSafeArea()
    }

    /// dirty but works
    private fun hackSafeArea() {
        val layout = (mapFragment.view as? ViewGroup)?.getChildAt(0) as? ViewGroup
        val uiComponent = layout?.children?.findLast { it.javaClass.name == "com.tomtom.sdk.map.display.ui.UiComponentsView" } as? ViewGroup
        uiComponent?.fitsSystemWindows = true
        val compassButton = uiComponent?.children?.findLast { it.javaClass.name == "com.tomtom.sdk.map.display.ui.compass.DefaultCompassButton" }
        val layoutParams = compassButton?.layoutParams as? ViewGroup.MarginLayoutParams
        layoutParams?.let {
            it.setMargins(it.leftMargin, it.topMargin + dp2px(65), it.rightMargin, it.bottomMargin)
        }
        compassButton?.layoutParams = layoutParams
    }

    private fun setupMapContainer(): FragmentContainerView {
        val mapContainer = FragmentContainerView(this)
        mapContainer.id = mapContainerId
        mapContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        supportFragmentManager.beginTransaction().apply {
            add(mapContainerId, mapFragment)
            commit()
        }
        mapFragment.getMapAsync {
            mainViewModel.setupMap(it)
        }
        return mapContainer
    }

    private fun setupSearchView(): View {
        searchView = ComposeView(this).apply {
            val factory = SearchViewModel.Factory(
                locationProvider,
                SearchRepositoryImpl(onlineSearch),
                navigationRepository
            )
            setContent {
                TommySearchView(factory)
            }
        }
        searchView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        return searchView
    }

    private fun setupSimulationButton(): Button {
        simulationButton = Button(this)
        simulationButton.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp2px(20)
            bottomMargin = dp2px(70)
        }
        simulationButton.text = "Start Simulation"
        simulationButton.setOnClickListener {
            if (!isLocationPermissionGranted) {
                Toast.makeText(this@MainActivity, "Please allow location permissions", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            simulationButton.visibility = ViewGroup.GONE
            searchView.visibility = ViewGroup.GONE
            mainViewModel.startNavigation()
        }
        simulationButton.visibility = ViewGroup.GONE
        return simulationButton
    }

    private fun setupNavigationUi() {
        supportFragmentManager.beginTransaction().apply {
            add(mapContainerId, navigationFragment)
            commit()
        }
        navigationFragment.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event.targetState == Lifecycle.State.STARTED) {
                    navigationFragment.navigationView.hideSpeedView()
                    navigationFragment.lifecycle.removeObserver(this)

                    ViewCompat.setOnApplyWindowInsetsListener(navigationFragment.navigationView) { v, insets ->
                        v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                        insets
                    }
                }
            }
        })
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            mainViewModel.grantLocationPermission(true)
            setupNavigationUi()
            return
        }
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val granted = it[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                    && it[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            mainViewModel.grantLocationPermission(granted)
            if (granted) {
                setupNavigationUi()
            }
        }.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun configureViewModel() {
        lifecycleScope.launch {
            mainViewModel.selectedRoutePlan.collect {
                if (it != null) {
                    simulationButton.visibility = ViewGroup.VISIBLE
                }
            }
        }
        lifecycleScope.launch {
            mainViewModel.navigationStarted.drop(1).collect {
                if (it) configureNavigationStart() else configureNavigationStop()
            }
        }
        lifecycleScope.launch {
            mainViewModel.destinationArrived.drop(1).collect {
                if (it) {
                    navigationFragment.navigationView.showArrivalView()
                } else {
                    navigationFragment.navigationView.hideArrivalView()
                }
            }
        }
        lifecycleScope.launch {
            mainViewModel.announcementMessage.collect {
                if (it.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun configureNavigationStart() {
        searchView.visibility = ViewGroup.GONE
        simulationButton.visibility = ViewGroup.GONE
        mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.Invisible
        navigationFragment.setTomTomNavigation(tomTomNavigationProvider.value)
        navigationFragment.navigationView.showSpeedView()
        navigationFragment.navigationView.showGuidanceView()
        navigationFragment.startNavigation(mainViewModel.selectedRoutePlan.value!!)
        navigationFragment.addNavigationListener(navigationListener)
    }

    private fun configureNavigationStop() {
        searchView.visibility = ViewGroup.VISIBLE
        mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.InvisibleWhenRecentered
        navigationFragment.stopNavigation()
        navigationFragment.removeNavigationListener(navigationListener)
    }
}
