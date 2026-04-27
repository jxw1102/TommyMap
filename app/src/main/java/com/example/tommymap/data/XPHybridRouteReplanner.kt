package com.example.tommymap.data

import android.util.Log
import com.tomtom.quantity.Distance
import com.tomtom.sdk.annotations.InternalTomTomSdkApi
import com.tomtom.sdk.common.Result
import com.tomtom.sdk.navigation.NavigationSnapshot
import com.tomtom.sdk.navigation.currentActiveRoutePlanningOptions
import com.tomtom.sdk.navigation.replanning.RouteReplanningFailure
import com.tomtom.sdk.navigation.routereplanner.RouteReplanner
import com.tomtom.sdk.navigation.routereplanner.RouteReplannerResponse
import com.tomtom.sdk.navigation.routereplanner.common.removeDepartInstruction
import com.tomtom.sdk.navigation.routereplanner.hybrid.HybridRouteReplannerFactory
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.common.ExperimentalBackToRouteApi
import java.util.Locale

/**
 * Verbatim copy of XPENG's `XPHybridRouteReplanner` from
 * com.xiaopeng.montecarlo.tomtom.adapter.navi.internal.process, with the
 * AdpLog/LanguageUtil deps swapped for android.util.Log + a fixed Locale so it
 * builds outside their app. Used so our reproduction setup is byte-equivalent
 * to the customer's call chain for XPENG-1106.
 */
class XPHybridRouteReplanner(
    onlineRoutePlanner: RoutePlanner,
    offlineRoutePlanner: RoutePlanner,
    private val replannerListener: (Result<RouteReplannerResponse, RouteReplanningFailure>) -> Unit,
) : RouteReplanner {
    private val TAG = "replanner"
    private val routePlanner by lazy {
        HybridRouteReplannerFactory.create(onlineRoutePlanner, offlineRoutePlanner)
    }

    private var lastTime = 0L

    @OptIn(InternalTomTomSdkApi::class)
    @ExperimentalBackToRouteApi
    override fun backToRoute(navigationSnapshot: NavigationSnapshot): Result<RouteReplannerResponse, RouteReplanningFailure> {
        val res = routePlanner.backToRoute(navigationSnapshot)
        if (res.isSuccess()) {
            val routeList = res.value().routes.map {
                it.removeDepartInstruction(
                    Distance.meters(15),
                    Locale.getDefault()
                )
            }
            val result = Result.success(
                RouteReplannerResponse(routeList, res.value().routePlanningOptions)
            )
            replannerListener.invoke(result)
            if (android.os.SystemClock.uptimeMillis() - lastTime > 2000) {
                Log.i(
                    TAG,
                    "backToRoute success,evr=${navigationSnapshot.currentActiveRoutePlanningOptions.chargingOptions != null}" +
                        "${navigationSnapshot.locationSnapshot.rawLocation}"
                )
                lastTime = android.os.SystemClock.uptimeMillis()
            }
            return result
        }
        if (android.os.SystemClock.uptimeMillis() - lastTime > 2000) {
            Log.i(TAG, "plan fail")
            Log.i(
                TAG,
                "backToRoute success,evr=${navigationSnapshot.currentActiveRoutePlanningOptions.chargingOptions != null}" +
                    "${navigationSnapshot.locationSnapshot.rawLocation}"
            )
            lastTime = android.os.SystemClock.uptimeMillis()
        }
        replannerListener.invoke(res)
        return res
    }

    override fun close() = routePlanner.close()

    override fun fullReplan(navigationSnapshot: NavigationSnapshot): Result<RouteReplannerResponse, RouteReplanningFailure> {
        return routePlanner.fullReplan(navigationSnapshot)
    }

    override fun incrementRouteContents(navigationSnapshot: NavigationSnapshot): Result<RouteReplannerResponse, RouteReplanningFailure> {
        return routePlanner.incrementRouteContents(navigationSnapshot)
    }

    override fun update(navigationSnapshot: NavigationSnapshot): Result<RouteReplannerResponse, RouteReplanningFailure> {
        return routePlanner.update(navigationSnapshot)
    }
}
