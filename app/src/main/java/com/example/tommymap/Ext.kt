package com.example.tommymap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun Context.dp2px(dp: Int): Int {
    val scale = resources.displayMetrics.density
    return (dp * scale).toInt()
}

val Context.isLocationPermissionGranted: Boolean
    get() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun <T> List<T>.withinLength5(): List<T> {
    return if (this.size > 5) {
        this.subList(0, 5)
    } else {
        this
    }
}