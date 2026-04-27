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
import com.example.tommymap.data.TommyRouteReplanningEngine
import com.example.tommymap.data.XPHybridRouteReplanner
import com.example.tommymap.dp2px
import com.example.tommymap.isLocationPermissionGranted
import com.example.tommymap.ui.debug.ReplanStatusCard
import com.example.tommymap.ui.debug.ThreadDebugCard
import com.example.tommymap.ui.search.SearchViewModel
import com.example.tommymap.ui.search.TommySearchView
import com.tomtom.quantity.Distance
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStore
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStoreConfiguration
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProviderConfig
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton
import com.tomtom.sdk.navigation.UnitSystemType
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.replanning.RouteReplanningEngineFactory
import com.tomtom.sdk.navigation.routereplanner.hybrid.HybridRouteReplannerFactory
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
    // HybridRouteReplanner exercises the runBlocking(Dispatchers.IO.limitedParallelism(1))
    // codepath inside RouteReplannerService.fullReplan that XPENG-1106 hangs in.
    // We pass the OnlineRoutePlanner for both slots since we have no offline NDS
    // store; the SDK races them and either wins is fine for reproduction.
    // Mirrors XPENG's wrapper verbatim so our repro is byte-equivalent to their
    // production call chain. fullReplan/update/incrementRouteContents are all
    // pure passthroughs to HybridRouteReplannerFactory; only backToRoute is
    // customized (removes depart instructions). The customer's source is in
    // XPHybridRouteReplanner.kt.
    private val routeReplannerProvider = lazy {
        XPHybridRouteReplanner(
            onlineRoutePlanner = routePlanner,
            offlineRoutePlanner = routePlanner,
            replannerListener = { result ->
                android.util.Log.i(
                    "TommyMain",
                    "[${Thread.currentThread().name}] XPHybridRouteReplanner.listener -> " +
                        if (result.isSuccess()) "success ${result.value().routes.size} route(s)"
                        else "failure ${result.failure()}"
                )
            }
        )
    }
    private val routeReplanningEngineProvider = lazy {
        TommyRouteReplanningEngine(
            RouteReplanningEngineFactory.create(routeReplannerProvider.value)
        )
    }

    // Dedicated AndroidLocationProvider for the navigation engine. We keep this
    // separate from `locationProvider` (the map's wrapper) so the wrapper can
    // switch its inner delegate to MapMatchedLocationProvider during navigation
    // without creating a circular dependency on the engine.
    private val navLocationProviderLazy = lazy {
        AndroidLocationProvider(
            this,
            AndroidLocationProviderConfig(250.milliseconds, Distance.meters(20.0))
        ).also { if (isLocationPermissionGranted) it.enable() }
    }
    private val navLocationProvider: LocationProvider get() = navLocationProviderLazy.value

    private val tomTomNavigationProvider = lazy {
        OnlineTomTomNavigationFactory.create(
            Configuration(
                context = this,
                navigationTileStore = tileStore,
                locationProvider = navLocationProvider,
                routePlanner = routePlanner,
                routeReplanningEngine = routeReplanningEngineProvider.value,
            )
        )
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
        frameLayout.addView(setupThreadDebugCard())
        frameLayout.addView(setupDebugToolsColumn())
        frameLayout.addView(setupReplanStatusCard())

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
        if (routeReplanningEngineProvider.isInitialized()) {
            routeReplanningEngineProvider.value.close()
        }
        if (navLocationProviderLazy.isInitialized()) {
            navLocationProviderLazy.value.close()
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
        simulationButton.text = "Start Navigation"
        simulationButton.setOnClickListener {
            if (mainViewModel.navigationStarted.value) {
                mainViewModel.stopNavigation()
                return@setOnClickListener
            }
            if (!isLocationPermissionGranted) {
                Toast.makeText(this@MainActivity, "Please allow location permissions", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            mainViewModel.startNavigation()
        }
        simulationButton.visibility = ViewGroup.GONE
        return simulationButton
    }

    private val ioStressJobs = mutableListOf<kotlinx.coroutines.Job>()
    private var ioStressLatch: java.util.concurrent.CountDownLatch? = null

    private fun setupDebugToolsColumn(): View {
        val column = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = dp2px(12)
                topMargin = dp2px(110)
            }
        }

        val stressButton = Button(this).apply { text = "Stress IO" }
        stressButton.setOnClickListener {
            if (ioStressLatch == null) {
                // Park 64 IO workers on a single CountDownLatch.await(), which
                // blocks the underlying thread via LockSupport.park. The IO pool
                // can no longer hand out workers, so any subsequent
                // runBlocking(IO.limitedParallelism(1)) — e.g. inside the SDK's
                // RouteReplannerService.fullReplan — has to wait.
                val latch = java.util.concurrent.CountDownLatch(1)
                ioStressLatch = latch
                repeat(64) {
                    ioStressJobs += lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        kotlinx.coroutines.runInterruptible { latch.await() }
                    }
                }
                stressButton.text = "Unstress IO"
                Toast.makeText(this@MainActivity, "Parked 64 IO workers on CountDownLatch", Toast.LENGTH_SHORT).show()
            } else {
                ioStressLatch?.countDown()
                ioStressLatch = null
                ioStressJobs.clear()
                stressButton.text = "Stress IO"
                Toast.makeText(this@MainActivity, "Released IO pool", Toast.LENGTH_SHORT).show()
            }
        }
        column.addView(stressButton)

        val dumpButton = Button(this).apply { text = "Dump threads" }
        dumpButton.setOnClickListener {
            val sb = StringBuilder("=== THREAD DUMP @ ${System.currentTimeMillis()} ===\n")
            Thread.getAllStackTraces().toSortedMap(compareBy { it.name }).forEach { (thread, stack) ->
                sb.append("\n--- ${thread.name} (tid=${thread.id} state=${thread.state}) ---\n")
                stack.take(40).forEach { sb.append("  at $it\n") }
            }
            // Logcat caps single messages at ~4KB; chunk it.
            sb.toString().chunked(3500).forEachIndexed { i, chunk ->
                android.util.Log.w("TommyDump", "[part $i] $chunk")
            }
            Toast.makeText(this@MainActivity, "Dumped ${Thread.activeCount()} threads to logcat (TommyDump)", Toast.LENGTH_SHORT).show()
        }
        column.addView(dumpButton)

        return column
    }

    private fun setupReplanStatusCard(): View {
        val card = ComposeView(this).apply {
            setContent {
                ReplanStatusCard(statusFlow = routeReplanningEngineProvider.value.status)
            }
        }
        card.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp2px(110)
        }
        return card
    }

    private fun setupThreadDebugCard(): View {
        val card = ComposeView(this).apply {
            setContent { ThreadDebugCard() }
        }
        card.layoutParams = FrameLayout.LayoutParams(
            dp2px(380),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp2px(12)
            bottomMargin = dp2px(70)
        }
        return card
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
                simulationButton.visibility = if (it != null || mainViewModel.navigationStarted.value) {
                    ViewGroup.VISIBLE
                } else {
                    ViewGroup.GONE
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
        simulationButton.text = "Stop"
        simulationButton.visibility = ViewGroup.VISIBLE
        mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.Invisible
        navigationFragment.setTomTomNavigation(tomTomNavigationProvider.value)
        navigationFragment.navigationView.showSpeedView()
        navigationFragment.navigationView.showGuidanceView()
        // The TomTomNavigation engine is already started by MainViewModel on the
        // navigation dispatcher; we no longer call navigationFragment.startNavigation.
        // Trigger map setup directly since NavigationListener.onStarted won't fire.
        mainViewModel.onNavigationStarted(resources.getDimension(R.dimen.map_padding_bottom).toInt())
    }

    private fun configureNavigationStop() {
        searchView.visibility = ViewGroup.VISIBLE
        simulationButton.text = "Start Navigation"
        mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.InvisibleWhenRecentered
        navigationFragment.navigationView.hideSpeedView()
        navigationFragment.navigationView.hideGuidanceView()
    }
}
