/*
 * NothingCamera – private, offline camera for Android
 * Copyright (C) 2026 Real3rr0r303YT
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */


package com.real3rr0r303yt.nothingcamera.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingRed
import com.real3rr0r303yt.nothingcamera.ui.theme.NothingWhite
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Glass fills/strokes shared by the slider pieces. */
private object Glass {
    val fill = Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x14FFFFFF), Color(0x24FFFFFF)))
    val rim = Brush.linearGradient(listOf(Color(0xB3FFFFFF), Color(0x1AFFFFFF), Color(0x14FFFFFF), Color(0x80FFFFFF)))
    val bubble = Brush.radialGradient(listOf(Color(0x55FFFFFF), Color(0x22FFFFFF), Color(0x10FFFFFF)))
    val tint = Color(0x59000000)
}

private val STOPS = listOf(1f, 2f, 5f, 10f, 20f, 40f)

/**
 * Liquid-glass zoom slider (1× … 40×, logarithmic): a frosted capsule with a Nothing dot track,
 * a glass bubble that swells while held and stretches like liquid when dragged fast, haptic
 * ticks at 1 · 2 · 5 · 10 · 20 · 40 with a gentle snap. A small 0.6 bubble appears for ultra-wide.
 */
@Composable
fun LiquidZoomSlider(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    angle: Float,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Zoom",
) {
    val lo = maxOf(1f, minZoom)
    val hi = maxZoom
    if (hi <= lo + 0.05f) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoom by rememberUpdatedState(onZoom)

    var widthPx by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var stretchTarget by remember { mutableFloatStateOf(1f) }
    var lastStop by remember { mutableIntStateOf(-1) }

    val scale by animateFloatAsState(
        if (pressed) 1.18f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "bubbleScale",
    )
    val stretch by animateFloatAsState(
        stretchTarget,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow),
        label = "bubbleStretch",
    )
    LaunchedEffect(stretchTarget) {
        if (stretchTarget != 1f) { delay(70); stretchTarget = 1f }
    }

    val padPx = with(density) { 30.dp.toPx() }
    val span = ln(hi / lo)
    fun fracOf(z: Float) = (ln(z.coerceIn(lo, hi) / lo) / span).coerceIn(0f, 1f)
    fun zoomAt(x: Float): Float {
        val usable = (widthPx - 2 * padPx).coerceAtLeast(1f)
        var f = ((x - padPx) / usable).coerceIn(0f, 1f)
        // Gentle magnetic snap to the labelled stops.
        STOPS.filter { it in lo..hi }.forEach { s -> if (abs(f - fracOf(s)) < 0.025f) f = fracOf(s) }
        return lo * (hi / lo).pow(f)
    }

    Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (minZoom < 0.95f) {
            GlassBubble(
                text = formatZoom(minZoom),
                selected = zoom < 0.98f,
                angle = angle,
                onClick = { onZoom(minZoom) },
            )
            Spacer(Modifier.width(10.dp))
        }

        val textMeasurer = rememberTextMeasurer()
        Box(
            Modifier
                .weight(1f)
                .height(58.dp)
                .shadow(18.dp, RoundedCornerShape(50), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(50))
                .background(Glass.tint)
                .background(Glass.fill)
                .border(1.dp, Glass.rim, RoundedCornerShape(50))
                .onSizeChanged { widthPx = it.width }
                .semantics {
                    contentDescription = label
                    stateDescription = formatZoom(zoom)
                }
                .pointerInput(lo, hi) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        var lastX = down.position.x
                        fun apply(x: Float) {
                            val z = zoomAt(x)
                            val stop = STOPS.indexOfLast { z + 0.01f >= it }
                            if (stop != lastStop) {
                                if (lastStop != -1) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastStop = stop
                            }
                            if (abs(z - currentZoom) > 0.001f) currentOnZoom(z)
                        }
                        apply(down.position.x)
                        drag(down.id) { change ->
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            stretchTarget = 1f + (abs(dx) / 28f).coerceAtMost(0.45f)
                            apply(change.position.x)
                            change.consume()
                        }
                        pressed = false
                    }
                },
        ) {
            // Specular highlight along the top edge – the "liquid glass" sheen.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(Color(0x40FFFFFF), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.5f,
                            ),
                            topLeft = Offset(size.height * 0.4f, 2.dp.toPx()),
                            size = Size(size.width - size.height * 0.8f, size.height * 0.42f),
                            cornerRadius = CornerRadius(size.height),
                        )
                    },
            )
            val frac = fracOf(zoom)
            Canvas(Modifier.fillMaxSize()) {
                val usable = size.width - 2 * padPx
                val cy = size.height * 0.42f
                // Nothing-style dot track: lit up to the bubble, dim after it.
                val step = 7.dp.toPx()
                var x = padPx
                while (x <= padPx + usable + 0.5f) {
                    val lit = (x - padPx) / usable <= frac
                    drawCircle(if (lit) Color(0xE6FFFFFF) else Color(0x40FFFFFF), radius = 1.3.dp.toPx(), center = Offset(x, cy))
                    x += step
                }
                // Stop marks with labels underneath.
                STOPS.filter { it in lo..hi }.forEach { s ->
                    val sx = padPx + fracOf(s) * usable
                    val active = zoom + 0.01f >= s
                    drawCircle(if (active) NothingRed else Color(0x99FFFFFF), radius = 2.6.dp.toPx(), center = Offset(sx, cy))
                    val layout = textMeasurer.measure(
                        formatZoom(s).removeSuffix("×"),
                        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xB3FFFFFF)),
                    )
                    drawText(layout, topLeft = Offset(sx - layout.size.width / 2f, cy + 7.dp.toPx()))
                }
            }

            // The glass bubble.
            if (widthPx > 0) {
                val usable = widthPx - 2 * padPx
                val cx = padPx + frac * usable
                val bubble = 44.dp
                val half = with(density) { bubble.toPx() / 2 }
                Box(
                    Modifier
                        .offset { IntOffset((cx - half).roundToInt(), 0) }
                        .align(Alignment.CenterStart)
                        .size(bubble)
                        .graphicsLayer {
                            scaleX = scale * stretch
                            scaleY = scale / (1f + (stretch - 1f) * 0.6f)
                        }
                        .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .background(Glass.bubble)
                        .border(1.2.dp, Glass.rim, CircleShape)
                        .drawBehind {
                            // Small specular dot, top-left.
                            drawOval(
                                Color(0x66FFFFFF),
                                topLeft = Offset(size.width * 0.22f, size.height * 0.1f),
                                size = Size(size.width * 0.38f, size.height * 0.2f),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatZoom(zoom),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (zoom >= 1.01f) NothingRed else NothingWhite,
                        modifier = Modifier.rotate(angle),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassBubble(text: String, selected: Boolean, angle: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Glass.tint)
            .background(Glass.bubble)
            .border(1.dp, if (selected) Brush.linearGradient(listOf(NothingRed, NothingRed)) else Glass.rim, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.removeSuffix("×"),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) NothingRed else NothingWhite,
            modifier = Modifier.rotate(angle),
        )
    }
}

fun formatZoom(z: Float): String {
    val rounded = if (z >= 10f) z.roundToInt().toFloat() else (z * 10).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else String.format(Locale.ROOT, "%.1f", rounded)
    return "$text×"
}
