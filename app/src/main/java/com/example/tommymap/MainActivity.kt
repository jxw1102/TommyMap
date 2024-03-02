package com.example.tommymap

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import com.example.tommymap.ui.TommySearchView
import com.tomtom.quantity.Distance
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.android.AndroidLocationProviderConfig
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : AppCompatActivity() {

    // dependencies
    private val locationProvider: LocationProvider by lazy {
        val config = AndroidLocationProviderConfig(250.milliseconds, Distance.meters(20.0))
        AndroidLocationProvider(this, config)
    }
    // ----

    private val mapFragment: MapFragment by lazy {
        MapFragment.newInstance(MapOptions(BuildConfig.TOMTOM_API_KEY))
    }

    private lateinit var mainViewModel: MainViewModel
    private lateinit var searchView: View

    private val mapContainerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = MainViewModel.Factory(locationProvider)
        mainViewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(frameLayout)

        frameLayout.addView(setupMapContainer())
        frameLayout.addView(setupSearchView())

        requestLocationPermission()
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
            setContent {
                TommySearchView()
            }
        }
        searchView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        return searchView
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
}