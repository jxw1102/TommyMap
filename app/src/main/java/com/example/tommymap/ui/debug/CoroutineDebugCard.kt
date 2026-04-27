package com.example.tommymap.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Walks the [Job] tree rooted at each given scope and lists every active
 * coroutine. Refreshes once per second.
 *
 * No bytecode instrumentation needed (so it works on Android), but it can only
 * see coroutines whose root scope we have a reference to. We pass in
 * viewModelScope (our app coroutines) and DefaultTomTomNavigation's internal
 * coroutineScope (grabbed via reflection in MainActivity) so the SDK's
 * NavigationProcessScheduler / StandaloneNavigationProcessTrigger / etc. show
 * up too.
 */
@Composable
fun CoroutineDebugCard(
    rootScopesProvider: () -> List<Pair<String, CoroutineScope>>,
    modifier: Modifier = Modifier,
) {
    var rows by remember { mutableStateOf<List<CoroutineRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val collected = mutableListOf<CoroutineRow>()
            rootScopesProvider().forEach { (label, scope) ->
                val rootJob = scope.coroutineContext[Job] ?: return@forEach
                collected += CoroutineRow(label, jobState(rootJob), depth = 0, isRoot = true)
                walk(rootJob, depth = 1, sink = collected)
            }
            rows = collected
            delay(1000)
        }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Coroutines (${rows.count { !it.isRoot }})",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        rows.forEach { row -> CoroutineLine(row) }
    }
}

@Composable
private fun CoroutineLine(row: CoroutineRow) {
    Row {
        if (row.depth > 0) {
            Text(
                text = "  ".repeat(row.depth) + "↳ ",
                color = Color(0xFF9E9E9E),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = row.name,
            color = if (row.isRoot) Color(0xFFFFEB3B) else nameColor(row.name),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (row.isRoot) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.state,
            color = stateColor(row.state),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private data class CoroutineRow(
    val name: String,
    val state: String,
    val depth: Int,
    val isRoot: Boolean = false,
)

private fun walk(job: Job, depth: Int, sink: MutableList<CoroutineRow>) {
    job.children.forEach { child ->
        val name = (child as? CoroutineScope)
            ?.coroutineContext
            ?.get(CoroutineName)
            ?.name
            ?: child.toString().substringBefore('@').substringBefore('{')
        sink += CoroutineRow(name, jobState(child), depth)
        walk(child, depth + 1, sink)
    }
}

private fun jobState(job: Job): String = when {
    job.isCancelled -> "CANCELLED"
    job.isCompleted -> "DONE"
    job.isActive -> "ACTIVE"
    else -> "NEW"
}

private fun nameColor(name: String): Color = when {
    name.contains("Navigation", ignoreCase = true) ||
        name.contains("TomTom", ignoreCase = true) ||
        name.contains("Routing", ignoreCase = true) ||
        name.contains("Search", ignoreCase = true) -> Color(0xFF64B5F6)
    else -> Color.White
}

private fun stateColor(state: String): Color = when (state) {
    "ACTIVE" -> Color(0xFF4CAF50)
    "DONE" -> Color(0xFF9E9E9E)
    "CANCELLED" -> Color(0xFFE57373)
    else -> Color.White
}
