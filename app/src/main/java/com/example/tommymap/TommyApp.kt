package com.example.tommymap

import android.app.Application
import com.tomtom.sdk.extension.library.NavSdkExtension

class TommyApp: Application() {

    init {
        NavSdkExtension().customizeLocationMarker(
            color = "#FFFFFF",
            ambient = 0.8,
            gamma = 3.6
        )
    }
}
