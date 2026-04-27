package com.example.tommymap.data

import android.content.Context
import com.example.tommymap.isLocationPermissionGranted
import com.tomtom.quantity.Distance
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProviderConfig
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.navigation.TomTomNavigation
import kotlin.time.Duration.Companion.milliseconds

class TommyLocationProvider(
    private val context: Context
): LocationProvider {

    private var _currentLocationProvider: LocationProvider

    private val listeners = mutableListOf<OnLocationUpdateListener>()

    override val lastKnownLocation: GeoLocation?
        get() = _currentLocationProvider.lastKnownLocation

    override fun addOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        listeners.add(listener)
        _currentLocationProvider.addOnLocationUpdateListener(listener)
    }

    override fun disable() {
        _currentLocationProvider.disable()
    }

    override fun enable() {
        _currentLocationProvider.enable()
    }

    override fun removeOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        listeners.remove(listener)
        _currentLocationProvider.removeOnLocationUpdateListener(listener)
    }

    override fun close() {
        _currentLocationProvider.close()
    }

    init {
        _currentLocationProvider = DummyLocationProvider()
    }

    fun useAndroidLocationProvider() {
        close()
        val config = AndroidLocationProviderConfig(250.milliseconds, Distance.meters(20.0))
        _currentLocationProvider = AndroidLocationProvider(context, config)
        if (context.isLocationPermissionGranted) {
            enable()
        }
        listeners.forEach { _currentLocationProvider.addOnLocationUpdateListener(it) }
    }

    fun useMapMatchedLocationProvider(navigation: TomTomNavigation) {
        close()
        _currentLocationProvider = MapMatchedLocationProvider(navigation)
        enable()
        listeners.forEach { _currentLocationProvider.addOnLocationUpdateListener(it) }
    }
}

class DummyLocationProvider(
    override val lastKnownLocation: GeoLocation? = null
) : LocationProvider {

    override fun addOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        // do nothing
    }

    override fun close() {
        // do nothing
    }

    override fun disable() {
        // do nothing
    }

    override fun enable() {
        // do nothing
    }

    override fun removeOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        // do nothing
    }

}
