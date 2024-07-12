package com.example.tommymap

import android.app.Application

class TommyApp: Application() {

    private fun prepareDir() {
        val dir = getExternalFilesDir(null)
        dir?.let {
            assert(it.exists())
        }
    }

    override fun onCreate() {
        super.onCreate()
        prepareDir()


    }
}