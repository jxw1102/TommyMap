package com.example.tommymap

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.ui.MapFragment

class MainActivity : AppCompatActivity() {

    private val mapFragment: MapFragment by lazy {
        MapFragment.newInstance(MapOptions(BuildConfig.TOMTOM_API_KEY))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(frameLayout)

        val mapContainer = FragmentContainerView(this)
        mapContainer.id = View.generateViewId()
        mapContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        frameLayout.addView(mapContainer)
        
        supportFragmentManager.beginTransaction().apply {
            add(mapContainer.id, mapFragment)
            commit()
        }
        mapFragment.getMapAsync {
            println(it)
        }
    }
}