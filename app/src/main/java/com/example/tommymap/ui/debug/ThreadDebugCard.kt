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
import kotlinx.coroutines.delay

/**
 * Live overlay listing JVM threads relevant to TomTom navigation, refreshing
 * once a second. Threads are tagged by role so it's easy to tell our app pools
 * (tommy-*) from the SDK's internal pools (DefaultDispatcher-worker-*, native
 * worker pools, etc.).
 *
 * Reference: TomTomNavigationFactory wires backgroundDispatcher =
 * Dispatchers.IO.limitedParallelism(1), so the SDK's per-tick navigation work
 * shows up here as a DefaultDispatcher-worker-N row going RUNNABLE/WAITING.
 */
@Composable
fun ThreadDebugCard(modifier: Modifier = Modifier) {
    var rows by remember { mutableStateOf<List<ThreadRow>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val all = Thread.getAllStackTraces().keys.mapNotNull { it.toRowOrNull() }
            // Collapse the DefaultDispatcher-worker-* horde into one summary row;
            // there can be 64+ of them and they're all interchangeable kotlinx-coroutines IO workers.
            val (workers, rest) = all.partition { it.name.startsWith("DefaultDispatcher-worker-") }
            val collapsed = if (workers.isEmpty()) emptyList() else {
                val byState = workers.groupingBy { it.state }.eachCount()
                val stateSummary = byState.entries.joinToString(" / ") { "${it.value} ${it.key}" }
                listOf(
                    ThreadRow(
                        name = "DefaultDispatcher-worker × ${workers.size}",
                        state = stateSummary,
                        tid = workers.first().tid,
                        role = Role.SDK_BG,
                    )
                )
            }
            rows = (rest + collapsed).sortedWith(compareBy({ it.role.order }, { it.name }))
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
            text = "Threads (${rows.size})",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        rows.forEach { row ->
            Row {
                Text(
                    text = row.role.tag,
                    color = row.role.color,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    softWrap = false,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.state,
                    color = stateColor(row.state),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "#${row.tid}",
                    color = Color(0xFF9E9E9E),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}

private data class ThreadRow(val name: String, val state: String, val tid: Long, val role: Role)

private enum class Role(val tag: String, val color: Color, val order: Int) {
    OURS("APP", Color(0xFF4CAF50), 0),
    MAIN("MAIN", Color(0xFFFFEB3B), 1),
    SDK_BG("SDK", Color(0xFF64B5F6), 2),
    SDK_RENDER("GFX", Color(0xFFBA68C8), 3),
}

private fun Thread.toRowOrNull(): ThreadRow? {
    val role = classify(name) ?: return null
    return ThreadRow(name, state.name, id, role)
}

private fun classify(name: String): Role? = when {
    // App pools we own.
    name.startsWith("tommy-") -> Role.OURS

    // Main looper thread that hosts UI + most SDK callbacks.
    name == "main" -> Role.MAIN

    // SDK background work via Dispatchers.IO.limitedParallelism(1) — the
    // NavigationProcessScheduler's per-tick coroutines run here.
    name.startsWith("DefaultDispatcher-worker-") -> Role.SDK_BG
    name == "DefaultExecutor" -> Role.SDK_BG
    name.contains("tomtom", ignoreCase = true) -> Role.SDK_BG
    name.startsWith("pool-") && name.contains("-thread-") -> Role.SDK_BG

    // Map render threads (OpenGL ES + HW accelerated UI).
    name.startsWith("GLThread") -> Role.SDK_RENDER
    name == "RenderThread" -> Role.SDK_RENDER
    name.startsWith("hwuiTask") -> Role.SDK_RENDER

    else -> null
}

private fun stateColor(state: String): Color = when (state) {
    "RUNNABLE" -> Color(0xFF4CAF50)
    "WAITING", "TIMED_WAITING" -> Color(0xFFFFB74D)
    "BLOCKED" -> Color(0xFFE57373)
    "TERMINATED" -> Color(0xFF9E9E9E)
    else -> Color.White
}
