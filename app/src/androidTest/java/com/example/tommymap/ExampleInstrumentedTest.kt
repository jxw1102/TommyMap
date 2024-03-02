package com.example.tommymap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tommymap.data.NavigationRepositoryImpl
import com.example.tommymap.data.SearchRepositoryImpl
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.search.online.OnlineSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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
    }

    @Test
    fun testRoutePlanning() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val routePlanner = OnlineRoutePlanner.create(appContext, BuildConfig.TOMTOM_API_KEY)
        val repo = NavigationRepositoryImpl(routePlanner)
        val cityHall = GeoPoint(48.8573, 2.3522)
        val louvreMuseum = GeoPoint(latitude=48.861018, longitude=2.335851)
        runTest {
            repo.planRoute(cityHall, louvreMuseum).collect { route ->
                println(route)
            }
        }
    }

}
