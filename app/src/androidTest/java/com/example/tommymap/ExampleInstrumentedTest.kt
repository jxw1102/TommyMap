package com.example.tommymap

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tommymap.data.SearchRepositoryImpl
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.search.online.OnlineSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.tommymap", appContext.packageName)
    }

    @Test
    fun testOnlineSearch() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val searchDataSource = OnlineSearch.create(appContext, BuildConfig.TOMTOM_API_KEY)
        val repo = SearchRepositoryImpl(searchDataSource)
        val paris = GeoPoint(48.8573, 2.3522)
        runTest {
            repo.search("Louvre", paris).collect {
                it.forEach { location ->
                    println("${location.name} $location")
                }
                assert(it.isNotEmpty())
            }
        }
        // GeoPoint(latitude=48.861018, longitude=2.335851)
    }
}
