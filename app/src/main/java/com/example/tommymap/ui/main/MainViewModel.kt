package com.example.tommymap.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tommymap.R
import com.example.tommymap.data.NavigationRepository
import com.example.tommymap.data.TommyDispatchers
import com.example.tommymap.data.TommyLocationProvider
import com.tomtom.quantity.Distance
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.common.screen.Padding
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.marker.MarkerOptions
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteClickListener
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.navigation.ActiveRouteChangedListener
import com.tomtom.sdk.navigation.DestinationArrivalListener
import com.tomtom.sdk.navigation.GuidanceUpdatedListener
import com.tomtom.sdk.navigation.NavigationOptions
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RouteAddedListener
import com.tomtom.sdk.navigation.RouteAddedReason
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.navigation.RouteRemovedListener
import com.tomtom.sdk.navigation.RouteTrackingStateUpdatedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.guidance.GuidanceAnnouncement
import com.tomtom.sdk.navigation.guidance.InstructionPhase
import com.tomtom.sdk.navigation.guidance.instruction.GuidanceInstruction
import com.tomtom.sdk.routing.route.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainViewModel(
    private val locationProvider: TommyLocationProvider,
    private val tomTomNavigationProvider: Lazy<TomTomNavigation>,
    private val navigationRepository: NavigationRepository
) : ViewModel() {

    private val destinationMarkerTag = "Destination"

    private val _permissionStateFlow = MutableStateFlow(false)
    private val _navigationStarted = MutableStateFlow(false)
    private val _destinationArrived = MutableStateFlow(false)
    private val _announcementMessage = MutableStateFlow("")

    val navigationStarted: StateFlow<Boolean> = _navigationStarted
    val destinationArrived: StateFlow<Boolean> = _destinationArrived
    val announcementMessage: StateFlow<String> = _announcementMessage

    private lateinit var tomTomMap: TomTomMap

    private val tomTomNavigation: TomTomNavigation
        get() = tomTomNavigationProvider.value

    private var routePlans: MutableList<RoutePlan> = mutableListOf()
    private val _selectedRoutePlan = MutableStateFlow<RoutePlan?>(null)
    val selectedRoutePlan: StateFlow<RoutePlan?> = _selectedRoutePlan

    private val origin: GeoPoint?
        get() = locationProvider.lastKnownLocation?.position

    // The SDK delivers callbacks on its own threads (typically main or DefaultDispatcher-worker-*).
    // We re-dispatch onto the navigation dispatcher so that all app-side handling of navigation
    // events runs on the dedicated single-thread pool — same as the customer's setup.
    private val routeAddedListener by lazy {
        RouteAddedListener { route, _, routeAddedReason ->
            onNavigationThread {
                Log.d("TommyMain", "[${currentThreadName()}] RouteAddedListener ${route.id} ${routeAddedReason.javaClass.name}")
                if (routeAddedReason !is RouteAddedReason.NavigationStarted) {
                    onMap {
                        drawRoute(
                            route = route,
                            color = RouteOptions.DEFAULT_UNREACHABLE_COLOR,
                            withDepartureMarker = false,
                            withZoom = false
                        )
                    }
                }
            }
        }
    }

    private val routeRemovedListener by lazy {
        RouteRemovedListener { route, _ ->
            onNavigationThread {
                Log.d("TommyMain", "[${currentThreadName()}] RouteRemovedListener ${route.id}")
                onMap { tomTomMap.routes.find { it.tag == route.id.toString() }?.remove() }
            }
        }
    }

    private val activeRouteChangedListener by lazy {
        ActiveRouteChangedListener { route ->
            onNavigationThread {
                Log.d("TommyMain", "[${currentThreadName()}] ActiveRouteChangedListener ${route.id}")
                onMap {
                    tomTomMap.routes.forEach {
                        it.color = if (it.tag == route.id.toString()) {
                            RouteOptions.DEFAULT_COLOR
                        } else {
                            RouteOptions.DEFAULT_UNREACHABLE_COLOR
                        }
                    }
                }
            }
        }
    }

    private val progressUpdatedListener = ProgressUpdatedListener { progress ->
        onNavigationThread {
            Log.d("TommyMain", "[${currentThreadName()}] ProgressUpdatedListener $progress")
            onMap { tomTomMap.routes.firstOrNull()?.progress = progress.distanceAlongRoute }
        }
    }

    private val destinationArrivalListener = DestinationArrivalListener { route ->
        onNavigationThread {
            Log.d("TommyMain", "[${currentThreadName()}] DestinationArrivalListener ${route.id}")
            _destinationArrived.value = true
        }
    }

    private val routeTrackingStateUpdatedListener = RouteTrackingStateUpdatedListener { state ->
        onNavigationThread {
            Log.d(
                "TommyMain",
                "[${currentThreadName()}] RouteTrackingStateUpdatedListener " +
                    "hasDeviated=${state.hasDeviated} " +
                    "followed=${state.followedRoutes.size} " +
                    "unfollowed=${state.unfollowedRoutes.size}"
            )
        }
    }

    private val routeClickListener = RouteClickListener { route ->
        if (tomTomMap.cameraTrackingMode == CameraTrackingMode.FollowRouteDirection) return@RouteClickListener
        _selectedRoutePlan.value = routePlans.first { it.route.id.toString() == route.tag }
        route.remove()
        tomTomMap.routes.forEach { it.color = RouteOptions.DEFAULT_UNREACHABLE_COLOR }
        drawRoute(_selectedRoutePlan.value!!.route, withZoom = false)
    }

    private val guidanceUpdatedListener = object : GuidanceUpdatedListener {
        override fun onAnnouncementGenerated(
            announcement: GuidanceAnnouncement,
            shouldPlay: Boolean
        ) {
            onNavigationThread {
                Log.d("TommyMain", "[${currentThreadName()}] GuidanceUpdated.onAnnouncementGenerated ${announcement.plainTextMessage}")
                _announcementMessage.value = announcement.plainTextMessage
            }
        }

        override fun onDistanceToNextInstructionChanged(
            distance: Distance,
            instructions: List<GuidanceInstruction>,
            currentPhase: InstructionPhase
        ) {
            onNavigationThread {
                Log.d("TommyMain", "[${currentThreadName()}] GuidanceUpdated.onDistanceToNextInstructionChanged $distance phase=${currentPhase.javaClass.simpleName}")
            }
        }

        override fun onInstructionsChanged(instructions: List<GuidanceInstruction>) {
            onNavigationThread {
                Log.d("TommyMain", "[${currentThreadName()}] GuidanceUpdated.onInstructionsChanged count=${instructions.size}")
            }
        }
    }

    private fun currentThreadName(): String = Thread.currentThread().name

    private fun onNavigationThread(block: () -> Unit) {
        viewModelScope.launch(TommyDispatchers.navigation) { block() }
    }

    override fun onCleared() {
        onMap { tomTomMap.setLocationProvider(null) }
        super.onCleared()
    }

    fun grantLocationPermission(granted: Boolean) {
        _permissionStateFlow.value = granted
    }

    fun setupMap(tomTomMap: TomTomMap) {
        this.tomTomMap = tomTomMap
        listenToCurrentPosition()
        listenToDestination()
        onMap { tomTomMap.addRouteClickListener(routeClickListener) }
    }

    fun startNavigation() {
        val routePlan = selectedRoutePlan.value ?: return
        _navigationStarted.value = true
        _destinationArrived.value = false
        _announcementMessage.value = ""
        // tomTomNavigation already holds the TommyLocationProvider wrapper from
        // the factory Configuration; the wrapper currently delegates to
        // AndroidLocationProvider, so navigation receives real device GPS.
        viewModelScope.launch(TommyDispatchers.navigation) {
            tomTomNavigation.addProgressUpdatedListener(progressUpdatedListener)
            tomTomNavigation.addRouteAddedListener(routeAddedListener)
            tomTomNavigation.addRouteRemovedListener(routeRemovedListener)
            tomTomNavigation.addActiveRouteChangedListener(activeRouteChangedListener)
            tomTomNavigation.addDestinationArrivalListener(destinationArrivalListener)
            tomTomNavigation.addGuidanceUpdatedListener(guidanceUpdatedListener)
            tomTomNavigation.addRouteTrackingStateUpdatedListener(routeTrackingStateUpdatedListener)
            tomTomNavigation.start(NavigationOptions(routePlan))
        }
    }

    fun onNavigationStarted(bottomPadding: Int) {
        onMap {
            tomTomMap.cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
            tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
            tomTomMap.setPadding(Padding(0, 0, 0, bottomPadding))
        }
        // The navigation engine has its own AndroidLocationProvider (configured
        // in MainActivity), so we're free to switch the map's wrapper to a
        // MapMatched provider here — the marker now snaps to the route.
        locationProvider.useMapMatchedLocationProvider(tomTomNavigation)
    }

    fun stopNavigation() {
        viewModelScope.launch(TommyDispatchers.navigation) {
            tomTomNavigation.stop()
            tomTomNavigation.removeProgressUpdatedListener(progressUpdatedListener)
            tomTomNavigation.removeRouteAddedListener(routeAddedListener)
            tomTomNavigation.removeRouteRemovedListener(routeRemovedListener)
            tomTomNavigation.removeActiveRouteChangedListener(activeRouteChangedListener)
            tomTomNavigation.removeDestinationArrivalListener(destinationArrivalListener)
            tomTomNavigation.removeGuidanceUpdatedListener(guidanceUpdatedListener)
            tomTomNavigation.removeRouteTrackingStateUpdatedListener(routeTrackingStateUpdatedListener)
        }
        _navigationStarted.value = false
        _destinationArrived.value = false
        _announcementMessage.value = ""
        // Restore the map's wrapper to raw Android GPS so the marker keeps
        // moving after we tear down the MapMatched delegate.
        locationProvider.useAndroidLocationProvider()
        onMap {
            tomTomMap.cameraTrackingMode = CameraTrackingMode.None
            tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
            tomTomMap.setPadding(Padding(0, 0, 0, 0))
            clearMap()
        }
        routePlans.clear()
        _selectedRoutePlan.value = null
        navigationRepository.clearDestination()
    }

    private fun listenToCurrentPosition() {
        onMap {
            tomTomMap.enableLocationMarker(LocationMarkerOptions(type = LocationMarkerOptions.Type.Pointer))
            moveMapCamera()
        }
        viewModelScope.launch {
            _permissionStateFlow.collect { granted ->
                if (!granted) return@collect
                locationProvider.useAndroidLocationProvider()
                onMap { tomTomMap.setLocationProvider(locationProvider.also { it.enable() }) }
                locationProvider.addOnLocationUpdateListener(object : OnLocationUpdateListener {
                    override fun onLocationUpdate(location: GeoLocation) {
                        onMap { moveMapCamera(location.position) }
                        locationProvider.removeOnLocationUpdateListener(this)
                    }
                })
            }
        }
    }

    private fun listenToDestination() {
        viewModelScope.launch {
            navigationRepository.destination.collect { value ->
                val destination = value ?: return@collect
                if (!_permissionStateFlow.value) {
                    onMap { showDestinationMarker(destination) }
                }
                val o = origin ?: return@collect
                navigationRepository.planRoute(o, destination).catch {
                    Log.e("TommyMain", "${it.javaClass.name} ${it.message}")
                }.collect { plans ->
                    _selectedRoutePlan.value = plans.first()
                    this@MainViewModel.routePlans = plans.toMutableList()
                    onMap {
                        tomTomMap.removeRoutes()
                        plans.drop(1).forEach { drawRoute(it.route, RouteOptions.DEFAULT_UNREACHABLE_COLOR, withDepartureMarker = true, withZoom = false) }
                        drawRoute(plans.first().route, RouteOptions.DEFAULT_COLOR, withDepartureMarker = true, withZoom = true)
                    }
                }
            }
        }
    }

    private fun moveMapCamera(position: GeoPoint? = locationProvider.lastKnownLocation?.position) {
        position?.let {
            tomTomMap.moveCamera(
                CameraOptions(
                    position = it,
                    zoom = 10.0,
                    tilt = 0.0
                )
            )
        }
    }

    private fun showDestinationMarker(destination: GeoPoint) {
        tomTomMap.removeMarkers(destinationMarkerTag)
        val markerOpt = MarkerOptions(
            destination,
            ImageFactory.fromResource(R.drawable.ic_pin),
            tag = destinationMarkerTag
        )
        tomTomMap.addMarker(markerOpt)
        tomTomMap.moveCamera(CameraOptions(destination, zoom = 10.0))
    }

    private fun clearMap() {
        tomTomMap.removeRoutes()
        tomTomMap.removeMarkers(destinationMarkerTag)
        moveMapCamera()
    }

    private fun drawRoute(
        route: Route,
        color: Int = RouteOptions.DEFAULT_COLOR,
        withDepartureMarker: Boolean = true,
        withZoom: Boolean = true
    ) {
        val instructions = route.legs
            .flatMap { routeLeg -> routeLeg.instructions }
            .map {
                Instruction(
                    routeOffset = it.routeOffset
                )
            }
        val routeOptions = RouteOptions(
            geometry = route.geometry,
            destinationMarkerVisible = true,
            departureMarkerVisible = withDepartureMarker,
            instructions = instructions,
            routeOffset = route.routePoints.map { it.routeOffset },
            color = color,
            tag = route.id.toString()
        )
        tomTomMap.addRoute(routeOptions)
        if (withZoom) {
            tomTomMap.zoomToRoutes(100)
        }
    }

    private fun onMap(block: () -> Unit) {
        viewModelScope.launch(TommyDispatchers.map) { block() }
    }

    class Factory(
        private val locationProvider: TommyLocationProvider,
        private val tomTomNavigationProvider: Lazy<TomTomNavigation>,
        private val navigationRepository: NavigationRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(locationProvider, tomTomNavigationProvider, navigationRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
