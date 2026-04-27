package com.example.tommymap.ui.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tommymap.data.ReplanStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Tiny rotating arc, drawn ourselves to dodge the version mismatch between
 * compose-bom 2023.08's animation-core and the newer material3
 * CircularProgressIndicator's keyframes API.
 */
@Composable
private fun Spinner(color: Color, modifier: Modifier = Modifier) {
    var rotation by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            rotation = (rotation + 12f) % 360f
            delay(33)
        }
    }
    Canvas(modifier = modifier.graphicsLayer { rotationZ = rotation }) {
        val stroke = size.minDimension / 9f
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * Pill-style status card that surfaces what TommyRouteReplanningEngine is
 * doing right now. Spinner while replan() is running; ✓/✗ pip for ~3s after
 * it returns; hidden otherwise.
 */
@Composable
fun ReplanStatusCard(
    statusFlow: StateFlow<ReplanStatus>,
    modifier: Modifier = Modifier,
) {
    val status by statusFlow.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        when (status) {
            is ReplanStatus.Idle -> visible = false
            is ReplanStatus.InProgress -> visible = true
            is ReplanStatus.Succeeded, is ReplanStatus.Failed -> {
                visible = true
                delay(3000)
                visible = false
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xEE000000))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when (val s = status) {
                ReplanStatus.InProgress -> {
                    Spinner(
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Replanning route…",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                is ReplanStatus.Succeeded -> {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Replanned · ${s.routeCount} route(s) · ${s.tookMs}ms",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                is ReplanStatus.Failed -> {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Replan failed · ${s.tookMs}ms",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                ReplanStatus.Idle -> Unit // not visible anyway
            }
        }
    }
}

