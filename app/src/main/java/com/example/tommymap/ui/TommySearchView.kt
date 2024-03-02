package com.example.tommymap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TommySearchView() {
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(10) {
                Text(text = "result $it")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TommySearchViewPreview() {
    MaterialTheme {
        TommySearchView()
    }
}
