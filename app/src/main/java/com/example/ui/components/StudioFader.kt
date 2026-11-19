package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import kotlin.math.abs

@Composable
fun StudioFader(
    bandIndex: Int,
    frequencyLabel: String,
    gainDb: Float,
    minDb: Float = -15f,
    maxDb: Float = 15f,
    enabled: Boolean = true,
    onGainChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    val currentOnGainChanged by rememberUpdatedState(onGainChanged)

    val formattedDb = when {
        abs(gainDb) < 0.05f -> "0.0"
        gainDb > 0f -> "+${String.format("%.1f", gainDb)}"
        else -> String.format("%.1f", gainDb)
    }

    val activeColor = when {
        !enabled -> TextMuted
        gainDb > 0.2f -> ElectricMint
        gainDb < -0.2f -> WarmAmber
        else -> ElectricCyan
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDragging) ConsoleSurfaceElevated else ConsoleSurface.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = if (isDragging) activeColor.copy(alpha = 0.65f) else ConsoleBorder.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .semantics { contentDescription = "EQ $frequencyLabel Hz $formattedDb dB" }
            .testTag("eq_fader_$bandIndex"),
    ) {
        Text(
            text = formattedDb,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (abs(gainDb) > 0.2f && enabled) activeColor else TextSecondary,
            maxLines = 1,
        )

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(174.dp)
                .pointerInput(minDb, maxDb) {
                    detectTapGestures(
                        onDoubleTap = { currentOnGainChanged(0f) },
                        onTap = { tap ->
                            val pad = 16.dp.toPx()
                            val usableH = (size.height - pad * 2f).coerceAtLeast(1f)
                            val frac = 1f - ((tap.y - pad) / usableH).coerceIn(0f, 1f)
                            val newDb = minDb + frac * (maxDb - minDb)
                            currentOnGainChanged(if (abs(newDb) < 0.6f) 0f else newDb)
                        }
                    )
                }
                .pointerInput(minDb, maxDb) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val pad = 16.dp.toPx()
                            val usableH = (size.height - pad * 2f).coerceAtLeast(1f)
                            val frac = 1f - ((change.position.y - pad) / usableH).coerceIn(0f, 1f)
                            val newDb = minDb + frac * (maxDb - minDb)
                            currentOnGainChanged(if (abs(newDb) < 0.45f) 0f else newDb)
                        }
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val padY = 14.dp.toPx()
                val trackH = (size.height - padY * 2f).coerceAtLeast(1f)
                val centerY = padY + trackH / 2f
                val trackW = 5.dp.toPx()

                // Scale ticks
                for (step in -2..2) {
                    val tickY = centerY + (step / 2f) * (trackH / 2f)
                    val tickHalf = if (step == 0) 11.dp.toPx() else 7.dp.toPx()
                    drawLine(
                        color = if (step == 0) ConsoleBorder else ConsoleBorder.copy(alpha = 0.55f),
                        start = Offset(centerX - tickHalf, tickY),
                        end = Offset(centerX + tickHalf, tickY),
                        strokeWidth = if (step == 0) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }

                // Recessed fader slot
                drawRoundRect(
                    color = ObsidianBg,
                    topLeft = Offset(centerX - trackW / 2f, padY),
                    size = Size(trackW, trackH),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f),
                )

                // Active glow fill from 0 dB center to thumb
                val norm = ((gainDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                val thumbY = padY + (1f - norm) * trackH
                val fillTop = minOf(centerY, thumbY)
                val fillBottom = maxOf(centerY, thumbY)
                if (fillBottom - fillTop > 1f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(activeColor, activeColor.copy(alpha = 0.55f)),
                            startY = fillTop,
                            endY = fillBottom,
                        ),
                        topLeft = Offset(centerX - trackW / 2f, fillTop),
                        size = Size(trackW, fillBottom - fillTop),
                        cornerRadius = CornerRadius(trackW / 2f, trackW / 2f),
                    )
                }

                // Tactile fader cap
                val capW = 24.dp.toPx()
                val capH = 14.dp.toPx()
                drawRoundRect(
                    color = if (isDragging) activeColor else Color(0xFF2B3548),
                    topLeft = Offset(centerX - capW / 2f, thumbY - capH / 2f),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
                // Center LED strip on fader cap
                drawLine(
                    color = if (isDragging) ObsidianBg else activeColor,
                    start = Offset(centerX - capW * 0.32f, thumbY),
                    end = Offset(centerX + capW * 0.32f, thumbY),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
        )
    }
}
