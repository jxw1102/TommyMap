package com.example.tommymap

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tommymap.data.NavigationRepository
import com.tomtom.quantity.Speed
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
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
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RouteAddedListener
import com.tomtom.sdk.navigation.RouteAddedReason
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.navigation.RouteRemovedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.routing.route.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    private val locationProvider: LocationProvider,
    private val navigationRepository: NavigationRepository
) : ViewModel() {

//    private val destinationMarkerTag = "Destination"

    private val _permissionStateFlow = MutableStateFlow(false)
    private val _navigationStarted = MutableStateFlow(false)
    private val _destinationArrived = MutableStateFlow(false)

    val navigationStarted: StateFlow<Boolean> = _navigationStarted
    val destinationArrived: StateFlow<Boolean> = _destinationArrived

    private lateinit var tomTomMap: TomTomMap
    private lateinit var tomTomNavigation: TomTomNavigation

    private var routePlans: MutableList<RoutePlan> = mutableListOf()
    private val _selectedRoutePlan = MutableStateFlow<RoutePlan?>(null)
    val selectedRoutePlan: StateFlow<RoutePlan?> = _selectedRoutePlan

    private val origin: GeoPoint
        get() = locationProvider.lastKnownLocation?.position ?: GeoPoint(0.0, 0.0)

    private val routeAddedListener by lazy {
        RouteAddedListener { route, _, routeAddedReason ->
            Log.d("TommyMain", "RouteAddedListener ${route.id} ${routeAddedReason.javaClass.name}")
            if (routeAddedReason !is RouteAddedReason.NavigationStarted) {
                drawRoute(
                    route = route,
                    color = RouteOptions.DEFAULT_UNREACHABLE_COLOR,
                    withDepartureMarker = false,
                    withZoom = false
                )
            }
        }
    }

    private val routeRemovedListener by lazy {
        RouteRemovedListener { route, _ ->
            Log.d("TommyMain", "RouteRemovedListener ${route.id}")
            tomTomMap.routes.find { it.tag == route.id.toString() }?.remove()
        }
    }

    private val activeRouteChangedListener by lazy {
        ActiveRouteChangedListener { route ->
            Log.d("TommyMain", "ActiveRouteChangedListener ${route.id}")
            tomTomMap.routes.forEach {
                if (it.tag == route.id.toString()) {
                    it.color = RouteOptions.DEFAULT_COLOR
                } else {
                    it.color = RouteOptions.DEFAULT_UNREACHABLE_COLOR
                }
            }
        }
    }

    private val progressUpdatedListener = ProgressUpdatedListener {
        Log.d("TommyMain", "ProgressUpdatedListener $it")
        tomTomMap.routes.first().progress = it.distanceAlongRoute
    }

    private val destinationArrivalListener = DestinationArrivalListener { route ->
        Log.d("TommyMain", "DestinationArrivalListener ${route.id}")
        _destinationArrived.value = true
    }

    private val routeClickListener = RouteClickListener { route ->
        if (tomTomMap.cameraTrackingMode == CameraTrackingMode.FollowRouteDirection) return@RouteClickListener
        _selectedRoutePlan.value = routePlans.first { it.route.id.toString() == route.tag }
        route.remove()
        tomTomMap.routes.forEach { it.color = RouteOptions.DEFAULT_UNREACHABLE_COLOR }
        drawRoute(_selectedRoutePlan.value!!.route, withZoom = false)
    }

    fun grantLocationPermission(granted: Boolean) {
        _permissionStateFlow.value = granted
    }

    fun setupMap(tomTomMap: TomTomMap) {
        this.tomTomMap = tomTomMap
        listenToCurrentPosition()
        listenToDestination()
        tomTomMap.addRouteClickListener(routeClickListener)
    }

    fun startNavigation(navigation: TomTomNavigation) {
        tomTomNavigation = navigation
        _navigationStarted.value = true
        _destinationArrived.value = false
        val strategy = InterpolationStrategy(
            locations = _selectedRoutePlan.value!!.route.geometry.map { GeoLocation(it) },
            startDelay = 1.seconds,
            broadcastDelay = 200.milliseconds,
            currentSpeed = Speed.metersPerSecond(25)
        )
        val simulationLocationProvider = SimulationLocationProvider.create(strategy).also { it.enable() }
        tomTomNavigation.locationProvider = simulationLocationProvider
        tomTomNavigation.addProgressUpdatedListener(progressUpdatedListener)
        tomTomNavigation.addRouteAddedListener(routeAddedListener)
        tomTomNavigation.addRouteRemovedListener(routeRemovedListener)
        tomTomNavigation.addActiveRouteChangedListener(activeRouteChangedListener)
        tomTomNavigation.addDestinationArrivalListener(destinationArrivalListener)
    }

    fun onNavigationStarted(bottomPadding: Int) {
        tomTomMap.cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
        tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
        val mapMatchedLocationProvider = MapMatchedLocationProvider(tomTomNavigation)
        tomTomMap.setLocationProvider(mapMatchedLocationProvider)
        tomTomMap.setPadding(Padding(0, 0, 0, bottomPadding))
    }

    fun stopNavigation() {
        tomTomNavigation.removeProgressUpdatedListener(progressUpdatedListener)
        tomTomNavigation.removeRouteAddedListener(routeAddedListener)
        tomTomNavigation.removeRouteRemovedListener(routeRemovedListener)
        tomTomNavigation.removeActiveRouteChangedListener(activeRouteChangedListener)
        tomTomNavigation.removeDestinationArrivalListener(destinationArrivalListener)
        _navigationStarted.value = false
        tomTomMap.cameraTrackingMode = CameraTrackingMode.None
        tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
        tomTomMap.setPadding(Padding(0, 0, 0, 0))
    }

    private fun listenToCurrentPosition() {
        val markerOptions = LocationMarkerOptions(type = LocationMarkerOptions.Type.Pointer)
        tomTomMap.enableLocationMarker(markerOptions)
        moveMapCamera()
        viewModelScope.launch {
            _permissionStateFlow.collect { granted ->
                if (!granted) return@collect
                tomTomMap.setLocationProvider(locationProvider.also { it.enable() })
                locationProvider.addOnLocationUpdateListener(object : OnLocationUpdateListener {
                    override fun onLocationUpdate(location: GeoLocation) {
                        moveMapCamera(location.position)
                        locationProvider.removeOnLocationUpdateListener(this)
                    }
                })
            }
        }
    }

    private fun listenToDestination() {
        viewModelScope.launch {
            navigationRepository.destination.collect { value ->
                value?.let { destination ->
//                    showDestinationMarker(destination)
                    navigationRepository.planRoute(origin, destination).catch {
                        Log.e("TommyMain", "${it.javaClass.name} ${it.message}")
                    }.collect { routePlans ->
                        this@MainViewModel.routePlans = routePlans.toMutableList()
                        tomTomMap.removeRoutes()
                        routePlans.drop(1).forEach { drawRoute(it.route, RouteOptions.DEFAULT_UNREACHABLE_COLOR, withDepartureMarker = true, withZoom = false) }
                        _selectedRoutePlan.value = routePlans.first()
                        drawRoute(routePlans.first().route, RouteOptions.DEFAULT_COLOR, withDepartureMarker = true, withZoom = true)
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
                    zoom = 10.0
                )
            )
        }
    }

//    private fun showDestinationMarker(destination: GeoPoint) {
//        tomTomMap.removeMarkers(destinationMarkerTag)
//        val markerOpt = MarkerOptions(
//            destination,
//            ImageFactory.fromResource(R.drawable.ic_pin),
//            tag = destinationMarkerTag
//        )
//        tomTomMap.addMarker(markerOpt)
//        tomTomMap.moveCamera(CameraOptions(destination, zoom = 15.0))
//    }

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

    class Factory(
        private val locationProvider: LocationProvider,
        private val navigationRepository: NavigationRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(locationProvider, navigationRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}