package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EqBandSpec
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import kotlin.math.abs

@Composable
fun SpectrumCurveCanvas(
    bands: List<Float>,
    bandSpecs: List<EqBandSpec>,
    spectrumBars: List<Float>,
    masterEnabled: Boolean,
    onBandChanged: (index: Int, gainDb: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    var activeDragIndex by remember { mutableIntStateOf(-1) }
    val currentBands by rememberUpdatedState(bands)
    val currentOnBandChanged by rememberUpdatedState(onBandChanged)

    val curveColor = if (masterEnabled) ElectricCyan else TextMuted
    val secondaryColor = if (masterEnabled) ElectricMint else TextMuted.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBg, ConsoleSurface)
                )
            )
            .border(1.dp, ConsoleBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("eq_spectrum_curve")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bandSpecs.size) {
                    detectTapGestures { offset ->
                        val count = bandSpecs.size.coerceAtLeast(1)
                        val padX = 24f
                        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                        val padY = 18f
                        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                        val nearest = (0 until count).minByOrNull { idx ->
                            val nodeX = padX + (idx.toFloat() / (count - 1).coerceAtLeast(1)) * usableW
                            abs(offset.x - nodeX)
                        } ?: 0
                        val normY = ((offset.y - padY) / usableH).coerceIn(0f, 1f)
                        val db = (1f - normY * 2f) * 15f
                        currentOnBandChanged(nearest, db)
                    }
                }
                .pointerInput(bandSpecs.size) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val count = bandSpecs.size.coerceAtLeast(1)
                            val padX = 24f
                            val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                            activeDragIndex = (0 until count).minByOrNull { idx ->
                                val nodeX = padX + (idx.toFloat() / (count - 1).coerceAtLeast(1)) * usableW
                                abs(startOffset.x - nodeX)
                            } ?: -1
                        },
                        onDragEnd = { activeDragIndex = -1 },
                        onDragCancel = { activeDragIndex = -1 },
                        onDrag = { change, _ ->
                            change.consume()
                            val idx = activeDragIndex
                            if (idx in currentBands.indices) {
                                val padY = 18f
                                val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                                val normY = ((change.position.y - padY) / usableH).coerceIn(0f, 1f)
                                val db = (1f - normY * 2f) * 15f
                                currentOnBandChanged(idx, db)
                            }
                        }
                    )
                }
        ) {
            val padLeft = 24.dp.toPx()
            val padRight = 14.dp.toPx()
            val padTop = 14.dp.toPx()
            val padBottom = 18.dp.toPx()
            val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
            val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)
            val centerY = padTop + chartH / 2f

            // 1. Horizontal dB Grid Lines (+15, 0, -15)
            val dbLevels = listOf(15f, 0f, -15f)
            val labelStyle = TextStyle(
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                color = TextMuted,
            )
            dbLevels.forEach { db ->
                val y = padTop + ((15f - db) / 30f) * chartH
                drawLine(
                    color = if (db == 0f) ConsoleBorder.copy(alpha = 0.9f) else ConsoleBorder.copy(alpha = 0.4f),
                    start = Offset(padLeft, y),
                    end = Offset(padLeft + chartW, y),
                    strokeWidth = if (db == 0f) 1.4.dp.toPx() else 0.8.dp.toPx(),
                )
                val label = if (db > 0) "+15" else if (db < 0) "-15" else "  0"
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(0f, y - 6.dp.toPx()),
                    style = labelStyle,
                )
            }

            // 2. Real-time Spectrum FFT Bars underneath the EQ curve
            val barCount = spectrumBars.size.coerceAtLeast(1)
            val slotW = chartW / barCount
            val barW = (slotW * 0.58f).coerceAtLeast(2.dp.toPx())
            spectrumBars.forEachIndexed { idx, level ->
                val norm = level.coerceIn(0.02f, 1f)
                val barH = norm * chartH * 0.92f
                val x = padLeft + idx * slotW + (slotW - barW) / 2f
                val y = padTop + chartH - barH
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = if (masterEnabled) 0.52f else 0.16f),
                            curveColor.copy(alpha = if (masterEnabled) 0.14f else 0.06f),
                        ),
                        startY = y,
                        endY = padTop + chartH,
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }

            // 3. Smooth Cubic Bezier Frequency Response Curve
            val count = bandSpecs.size.coerceAtLeast(2)
            val nodePoints = (0 until count).map { idx ->
                val gain = currentBands.getOrElse(idx) { 0f }.coerceIn(-15f, 15f)
                val x = padLeft + (idx.toFloat() / (count - 1).coerceAtLeast(1)) * chartW
                val y = padTop + ((15f - gain) / 30f) * chartH
                Offset(x, y)
            }

            val curvePath = Path()
            val fillPath = Path()
            if (nodePoints.isNotEmpty()) {
                curvePath.moveTo(nodePoints.first().x, nodePoints.first().y)
                fillPath.moveTo(nodePoints.first().x, centerY)
                fillPath.lineTo(nodePoints.first().x, nodePoints.first().y)

                for (i in 0 until nodePoints.size - 1) {
                    val p0 = nodePoints[i]
                    val p1 = nodePoints[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    curvePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(nodePoints.last().x, centerY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            curveColor.copy(alpha = 0.24f),
                            Color.Transparent,
                            WarmAmber.copy(alpha = 0.14f),
                        ),
                        startY = padTop,
                        endY = padTop + chartH,
                    ),
                )

                drawPath(
                    path = curvePath,
                    brush = Brush.horizontalGradient(listOf(curveColor, secondaryColor)),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // 4. Band Nodes & Frequency Axis Labels
            nodePoints.forEachIndexed { idx, pt ->
                val isDragging = idx == activeDragIndex
                val gain = currentBands.getOrElse(idx) { 0f }
                val dotColor = when {
                    !masterEnabled -> TextMuted
                    gain > 0.2f -> ElectricMint
                    gain < -0.2f -> WarmAmber
                    else -> ElectricCyan
                }

                // Vertical faint guide line
                drawLine(
                    color = ConsoleBorder.copy(alpha = 0.28f),
                    start = Offset(pt.x, padTop),
                    end = Offset(pt.x, padTop + chartH),
                    strokeWidth = 0.8.dp.toPx(),
                )

                if (isDragging) {
                    drawCircle(
                        color = dotColor.copy(alpha = 0.28f),
                        radius = 12.dp.toPx(),
                        center = pt,
                    )
                }
                drawCircle(
                    color = ObsidianBg,
                    radius = 5.dp.toPx(),
                    center = pt,
                )
                drawCircle(
                    color = dotColor,
                    radius = if (isDragging) 5.dp.toPx() else 3.6.dp.toPx(),
                    center = pt,
                )

                val freqLabel = bandSpecs.getOrNull(idx)?.label ?: ""
                if (freqLabel.isNotEmpty()) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = freqLabel,
                        topLeft = Offset(pt.x - 10.dp.toPx(), padTop + chartH + 4.dp.toPx()),
                        style = TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 8.5.sp,
                            color = if (isDragging) dotColor else TextSecondary,
                        ),
                    )
                }
            }
        }
    }
}
