package com.example.tommymap.data

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Mirrors the customer threading model used to reproduce the
 * "yawing not recalculating" report:
 *  - navigation: dedicated single-thread pool (60s idle reclaim)
 *  - routing:    dedicated single-thread pool (60s idle reclaim)
 *  - search:     standard IO pool
 *  - map:        main thread via Handler dispatcher
 */
object TommyDispatchers {

    private fun dedicatedSingleThreadPool(name: String): ExecutorCoroutineDispatcher {
        val executor = ThreadPoolExecutor(
            0,
            1,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue<Runnable>(),
            ThreadFactory { runnable -> Thread(runnable, name) }
        )
        return executor.asCoroutineDispatcher()
    }

    val navigation: ExecutorCoroutineDispatcher by lazy {
        dedicatedSingleThreadPool("tommy-navigation")
    }

    val routing: ExecutorCoroutineDispatcher by lazy {
        dedicatedSingleThreadPool("tommy-routing")
    }

    val search: CoroutineDispatcher = Dispatchers.IO

    val map: CoroutineDispatcher by lazy {
        Handler(Looper.getMainLooper()).asCoroutineDispatcher("tommy-map")
    }
}
