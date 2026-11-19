package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ConsoleSurfaceHighest
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber

@Composable
fun StereoVuMeter(
    leftDb: Float,
    rightDb: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        SingleChannelLedBar(channel = "L", db = leftDb)
        SingleChannelLedBar(channel = "R", db = rightDb)
    }
}

@Composable
private fun SingleChannelLedBar(
    channel: String,
    db: Float,
) {
    val norm = ((db + 60f) / 60f).coerceIn(0f, 1f)
    val displayDb = if (db <= -59f) "-∞" else String.format("%.1f", db)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = channel,
            style = MaterialTheme.typography.labelMedium,
            color = ElectricCyan,
            modifier = Modifier.width(16.dp),
        )

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
        ) {
            val segments = 24
            val gap = 3.dp.toPx()
            val segW = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(2f)
            val litCount = (norm * segments).toInt()

            for (i in 0 until segments) {
                val segColor = when {
                    i >= 21 -> PeakCoral
                    i >= 16 -> WarmAmber
                    else -> ElectricMint
                }
                val isLit = i < litCount
                drawRoundRect(
                    color = if (isLit) segColor else ConsoleSurfaceHighest.copy(alpha = 0.65f),
                    topLeft = Offset(i * (segW + gap), 0f),
                    size = Size(segW, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }

        Text(
            text = "$displayDb dB",
            style = MaterialTheme.typography.labelSmall,
            color = if (db > -3f) PeakCoral else TextSecondary,
            modifier = Modifier.width(56.dp),
        )
    }
}
