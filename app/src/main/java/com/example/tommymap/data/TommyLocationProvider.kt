package com.example.tommymap.data

import android.content.Context
import com.example.tommymap.isLocationPermissionGranted
import com.tomtom.quantity.Distance
import com.tomtom.quantity.Speed
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProviderConfig
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.routing.route.Route
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    companion object {
        fun createSimulationLocationProvider(route: Route): LocationProvider {
            val base = Date().time
            val strategy = InterpolationStrategy(
                locations = route.geometry.withIndex().map { GeoLocation(it.value, time = base + it.index * 2000) },
                startDelay = 1.seconds,
                broadcastDelay = 200.milliseconds,
                currentSpeed = Speed.metersPerSecond(25)
            )
            return SimulationLocationProvider.create(strategy).also { it.enable() }
        }
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
