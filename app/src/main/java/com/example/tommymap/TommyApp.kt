package com.example.tommymap

import android.app.Application
import com.tomtom.sdk.extension.library.NavSdkExtension

class TommyApp: Application() {

    override fun onCreate() {
        super.onCreate()
        NavSdkExtension().hookForSmoothTransition()
    }
}
