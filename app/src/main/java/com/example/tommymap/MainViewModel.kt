package com.example.tommymap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tommymap.data.NavigationRepository
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.image.ImageFactory
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.marker.MarkerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val locationProvider: LocationProvider,
    private val navigationRepository: NavigationRepository
) : ViewModel() {

    private val destinationMarkerTag = "Destination"

    private val _permissionStateFlow = MutableStateFlow(false)

    private lateinit var tomTomMap: TomTomMap

    private val origin: GeoPoint
        get() = locationProvider.lastKnownLocation?.position ?: GeoPoint(0.0, 0.0)

    fun grantLocationPermission(granted: Boolean) {
        _permissionStateFlow.value = granted
    }

    fun setupMap(tomTomMap: TomTomMap) {
        this.tomTomMap = tomTomMap
        listenToCurrentPosition()
        listenToDestination()
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
                    showDestinationMarker(destination)
                    navigationRepository.planRoute(origin, destination).collect {
                        println(it)
                        // draw route
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

    private fun showDestinationMarker(destination: GeoPoint) {
        tomTomMap.removeMarkers(destinationMarkerTag)
        val markerOpt = MarkerOptions(
            destination,
            ImageFactory.fromResource(R.drawable.ic_pin),
            tag = destinationMarkerTag
        )
        tomTomMap.addMarker(markerOpt)
        tomTomMap.moveCamera(CameraOptions(destination, zoom = 15.0))
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