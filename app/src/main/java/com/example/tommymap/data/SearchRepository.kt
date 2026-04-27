package com.example.tommymap.data

import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.search.Search
import com.tomtom.sdk.search.SearchOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface SearchRepository {
    fun search(text: String, geoBias: GeoPoint?): Flow<List<Location>>
}

class SearchRepositoryImpl(
    private val searchDataSource: Search,
    private val coroutineDispatcher: CoroutineDispatcher = TommyDispatchers.search
) : SearchRepository {

    override fun search(text: String, geoBias: GeoPoint?) = flow {
        val searchOptions = SearchOptions(
            query = text,
            geoBias = geoBias,
            limit = 10
        )
        val result = searchDataSource.search(searchOptions)
        if (result.isSuccess()) {
            val locations = result.value().results.map {
                Location(
                    it.poi?.names?.first() ?: it.place.address?.streetName ?: it.place.name,
                    it.place.address?.freeformAddress ?: "",
                    it.distance?.inMeters()?.toInt() ?: 0,
                    it.place.coordinate
                )
            }
            emit(locations)
        } else {
            throw SearchException(result.failure().message)
        }
    }.flowOn(coroutineDispatcher)

}

data class Location(
    val name: String,
    val address: String,
    val distance: Int,
    val coordinate: GeoPoint
)

class SearchException(
    override val message: String
) : Exception()
