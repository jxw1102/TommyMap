package com.example.tommymap.data

import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RouteInformationMode
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.options.calculation.AlternativeRoutesOptions
import com.tomtom.sdk.routing.options.guidance.ExtendedSections
import com.tomtom.sdk.routing.options.guidance.GuidanceOptions
import com.tomtom.sdk.routing.options.guidance.RoadShieldReferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface NavigationRepository {
    val destination: StateFlow<GeoPoint?>
    fun selectDestination(coordinate: GeoPoint)
    fun planRoute(origin: GeoPoint, destination: GeoPoint): Flow<List<RoutePlan>>
}

class NavigationRepositoryImpl(
    private val routePlanner: RoutePlanner,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NavigationRepository {

    private val _destination = MutableStateFlow<GeoPoint?>(null)
    override val destination: StateFlow<GeoPoint?> = _destination

    override fun selectDestination(coordinate: GeoPoint) {
        _destination.value = coordinate
    }

    override fun planRoute(origin: GeoPoint, destination: GeoPoint) = flow {
        val routePlanningOptions = RoutePlanningOptions(
            itinerary = Itinerary(origin, destination),
            mode = RouteInformationMode.Complete,
            alternativeRoutesOptions = AlternativeRoutesOptions(2),
            guidanceOptions = GuidanceOptions(extendedSections = ExtendedSections.All, roadShieldReferences = RoadShieldReferences.All)
        )
        val result = routePlanner.planRoute(routePlanningOptions)
        if (result.isSuccess()) {
            val routePlans = result.value().routes.map { RoutePlan(it, routePlanningOptions) }
            emit(routePlans)
        } else {
            throw NavigationException(result.failure().message)
        }
    }.flowOn(coroutineDispatcher)
}

class NavigationException(
    override val message: String
) : Exception()
