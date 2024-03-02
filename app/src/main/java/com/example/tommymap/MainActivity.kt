package com.example.tommymap

import android.Manifest
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.tommymap.data.NavigationRepository
import com.example.tommymap.data.NavigationRepositoryImpl
import com.example.tommymap.data.SearchRepositoryImpl
import com.example.tommymap.ui.SearchViewModel
import com.example.tommymap.ui.TommySearchView
import com.tomtom.quantity.Distance
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStore
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStoreConfiguration
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProviderConfig
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton
import com.tomtom.sdk.navigation.TomTomNavigation
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
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : AppCompatActivity() {

    // dependencies
    private val locationProvider: LocationProvider by lazy {
        val config = AndroidLocationProviderConfig(250.milliseconds, Distance.meters(20.0))
        AndroidLocationProvider(this, config)
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
    private val tomTomNavigation: TomTomNavigation by lazy {
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

        val factory = MainViewModel.Factory(locationProvider, navigationRepository)
        mainViewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(frameLayout)

        frameLayout.addView(setupMapContainer())
        frameLayout.addView(setupSearchView())
        frameLayout.addView(setupSimulationButton())
        setupNavigationUi()

        requestLocationPermission()
        configureViewModel()
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
            val factory = SearchViewModel.Factory(locationProvider, SearchRepositoryImpl(onlineSearch), navigationRepository)
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
            bottomMargin = dp2px(50)
        }
        simulationButton.text = "Start Simulation"
        simulationButton.setOnClickListener {
            simulationButton.visibility = ViewGroup.GONE
            searchView.visibility = ViewGroup.GONE
            mainViewModel.startNavigation(tomTomNavigation)
        }
        simulationButton.visibility = ViewGroup.GONE
        return simulationButton
    }

    private fun setupNavigationUi() {
        supportFragmentManager.beginTransaction().apply {
            add(mapContainerId, navigationFragment)
            commit()
        }
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            mainViewModel.grantLocationPermission(true)
            return
        }
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val granted = it[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                    && it[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            mainViewModel.grantLocationPermission(granted)
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
    }

    private fun configureNavigationStart() {
        searchView.visibility = ViewGroup.GONE
        simulationButton.visibility = ViewGroup.GONE
        mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.Invisible
        navigationFragment.setTomTomNavigation(tomTomNavigation)
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