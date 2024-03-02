package com.example.tommymap.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tommymap.BuildConfig
import com.example.tommymap.data.Location
import com.example.tommymap.data.SearchRepositoryImpl
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.search.online.OnlineSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TommySearchView(
    viewModelFactory: SearchViewModel.Factory,
    searchViewModel: SearchViewModel = viewModel(factory = viewModelFactory)
) {
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        withContext(Dispatchers.IO) {
            delay(300)
            searchViewModel.search(query)
        }
    }

    SearchBar(
        query = query,
        onQueryChange = { query = it },
        onSearch = { query = it },
        active = isSearching,
        onActiveChange = { isSearching = !isSearching },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        leadingIcon = {
            Icon(
                imageVector = if (isSearching) Icons.Default.ArrowBack else Icons.Default.Search,
                contentDescription = "Back",
                tint = Color.Black,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        isSearching = !isSearching
                    }
            )
        },
        trailingIcon = if (query.isNotEmpty()) {{
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                tint = Color.Black,
                modifier = Modifier.padding(8.dp).clickable { query = "" }
            )
        }} else null
    ) {
        val locations by searchViewModel.locations.collectAsState()
        val errorMessage by searchViewModel.errorMessage.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(errorMessage) {
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            items(locations) { location ->
                LocationRow(location) {
                    isSearching = false
                    query = location.name
                    // notify outside about selected location
                }
            }
        }
    }
}

@Composable
fun LocationRow(location: Location, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Text(
            text = location.name,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(10.dp)
        )
        Text(
            text = location.address,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Text(
            text = "${location.distance} m",
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                .fillMaxWidth()
        )
    }
}

class DummyLocationProvider(override val lastKnownLocation: GeoLocation?) : LocationProvider {
    override fun addOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        // do nothing
    }

    override fun close() {
        // do nothing
    }

    override fun disable() {
        // do nothing
    }

    override fun enable() {
        // do nothing
    }

    override fun removeOnLocationUpdateListener(listener: OnLocationUpdateListener) {
        // do nothing
    }

}

@Preview(showBackground = true)
@Composable
fun TommySearchViewPreview() {
    val paris = GeoPoint(48.8573, 2.3522)
    val context = LocalContext.current
    MaterialTheme {
        TommySearchView(
            SearchViewModel.Factory(
                DummyLocationProvider(GeoLocation(paris)),
                SearchRepositoryImpl(OnlineSearch.create(context, BuildConfig.TOMTOM_API_KEY))
            )
        )
    }
}
