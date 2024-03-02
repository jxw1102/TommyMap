package com.example.tommymap.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tommymap.data.Location
import com.example.tommymap.data.NavigationRepository
import com.example.tommymap.data.SearchRepository
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch

class SearchViewModel(
    private val locationProvider: LocationProvider,
    private val searchRepository: SearchRepository,
    private val navigationRepository: NavigationRepository
) : ViewModel() {

    private val _locations = MutableStateFlow(emptyList<Location>())
    val locations: StateFlow<List<Location>> = _locations

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _currentCoordinate: GeoPoint?
        get() = locationProvider.lastKnownLocation?.position

    suspend fun search(query: String) {
        Log.d("TommySearch", "Trigger search: $query")
        _errorMessage.value = null
        if (query.isEmpty()) {
            _locations.value = emptyList()
            return
        }
        searchRepository.search(query, _currentCoordinate).catch {
            Log.e("TommySearch", it.toString())
            _errorMessage.value = it.message ?: "An error occurred while searching"
        }.collect {
            _locations.value = it
            if (it.isEmpty()) {
                _errorMessage.value = "No result"
            }
        }
    }

    fun selectLocation(location: Location) {
        navigationRepository.selectDestination(location.coordinate)
    }

    class Factory(
        private val locationProvider: LocationProvider,
        private val searchRepository: SearchRepository,
        private val navigationRepository: NavigationRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(locationProvider, searchRepository, navigationRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
