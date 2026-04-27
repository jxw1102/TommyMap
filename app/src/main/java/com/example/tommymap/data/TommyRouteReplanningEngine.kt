package com.example.tommymap.data

import android.util.Log
import com.tomtom.sdk.common.Result
import com.tomtom.sdk.navigation.NavigationSnapshot
import com.tomtom.sdk.navigation.replanning.ReplannedRoute
import com.tomtom.sdk.navigation.replanning.RouteReplanningEngine
import com.tomtom.sdk.navigation.replanning.RouteReplanningEngineFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

sealed interface ReplanStatus {
    object Idle : ReplanStatus
    object InProgress : ReplanStatus
    data class Succeeded(val routeCount: Int, val tookMs: Long) : ReplanStatus
    data class Failed(val message: String, val tookMs: Long) : ReplanStatus
}

/**
 * Decorates a [RouteReplanningEngine] so that:
 *  - every shouldReplan / replan call is dispatched onto [TommyDispatchers.routing]
 *    (the dedicated single-thread pool that mirrors the customer's setup), and
 *  - each call logs the executing thread, the caller thread the SDK invoked us
 *    on, and the result. This makes it visible whether the customer's "yawing
 *    not recalculating" symptom shows up as shouldReplan returning false, replan
 *    returning a failure, or the call never happening at all.
 */
class TommyRouteReplanningEngine(
    private val delegate: RouteReplanningEngine,
) : RouteReplanningEngine {

    private val _status = MutableStateFlow<ReplanStatus>(ReplanStatus.Idle)
    val status: StateFlow<ReplanStatus> = _status

    override fun shouldReplan(navigationSnapshot: NavigationSnapshot): Boolean {
        val callerThread = Thread.currentThread().name
        return runBlocking(TommyDispatchers.routing) {
            val result = delegate.shouldReplan(navigationSnapshot)
            Log.d(
                "TommyMain",
                "[${Thread.currentThread().name}] shouldReplan -> $result " +
                    "(invoked from $callerThread)"
            )
            result
        }
    }

    override fun replan(
        navigationSnapshot: NavigationSnapshot,
    ): Result<List<ReplannedRoute>, RouteReplanningEngineFailure> {
        val callerThread = Thread.currentThread().name
        _status.value = ReplanStatus.InProgress
        val started = System.currentTimeMillis()
        return runBlocking(TommyDispatchers.routing) {
            val result = delegate.replan(navigationSnapshot)
            val elapsedMs = System.currentTimeMillis() - started
            val outcome = if (result.isSuccess()) {
                _status.value = ReplanStatus.Succeeded(result.value().size, elapsedMs)
                "${result.value().size} route(s)"
            } else {
                _status.value = ReplanStatus.Failed(result.failure().toString(), elapsedMs)
                "failure ${result.failure()}"
            }
            Log.d(
                "TommyMain",
                "[${Thread.currentThread().name}] replan in ${elapsedMs}ms -> $outcome " +
                    "(invoked from $callerThread)"
            )
            result
        }
    }

    override fun close() {
        delegate.close()
    }
}
