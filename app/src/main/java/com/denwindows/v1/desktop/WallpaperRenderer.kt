package com.denwindows.v1.desktop

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.denwindows.v1.data.BuiltInWallpaper
import com.denwindows.v1.theme.LocalDenColors

@Composable
fun WallpaperRenderer(
    wallpaperId: String,
    customWallpaperUri: String?,
    modifier: Modifier = Modifier
) {
    val denColors = LocalDenColors.current
    Box(modifier = modifier.fillMaxSize().background(denColors.desktopBackground)) {
        if (!customWallpaperUri.isNullOrBlank()) {
            AsyncImage(
                model = Uri.parse(customWallpaperUri),
                contentDescription = "Custom desktop wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            return@Box
        }

        when (BuiltInWallpaper.fromId(wallpaperId)) {
            BuiltInWallpaper.MIDNIGHT_BLOOM -> MidnightBloomCanvas(denColors.accent)
            BuiltInWallpaper.ARCTIC_SILK -> ArcticSilkCanvas(denColors.accent)
            BuiltInWallpaper.EMERALD_AURORA -> EmeraldAuroraCanvas()
            BuiltInWallpaper.OBSIDIAN_GRID -> ObsidianGridCanvas(denColors.accent)
            BuiltInWallpaper.PEARL_DAYLIGHT -> PearlDaylightCanvas(denColors.accent)
        }
    }
}

@Composable
private fun MidnightBloomCanvas(accent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF13223D), Color(0xFF0A0E16)),
                center = Offset(w * 0.55f, h * 0.45f),
                radius = maxOf(w, h) * 0.85f
            )
        )

        // Layered sculptural bloom ribbons inspired by modern glass folds
        val folds = listOf(
            Triple(0.50f, 0.50f, Color(0xFF1B4DB8)),
            Triple(0.53f, 0.46f, Color(0xFF2B6DF2)),
            Triple(0.47f, 0.54f, accent),
            Triple(0.56f, 0.52f, Color(0xFF66A4FF))
        )

        folds.forEachIndexed { index, (cxFrac, cyFrac, col) ->
            val path = Path().apply {
                val cx = w * cxFrac
                val cy = h * cyFrac
                val rx = w * (0.28f - index * 0.032f)
                val ry = h * (0.40f - index * 0.038f)
                moveTo(cx - rx, cy + ry * 0.35f)
                cubicTo(
                    cx - rx * 0.7f, cy - ry * 0.95f,
                    cx + rx * 0.65f, cy - ry * 0.85f,
                    cx + rx * 0.95f, cy - ry * 0.05f
                )
                cubicTo(
                    cx + rx * 1.05f, cy + ry * 0.75f,
                    cx - rx * 0.25f, cy + ry * 0.95f,
                    cx - rx, cy + ry * 0.35f
                )
                close()
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        col.copy(alpha = 0.32f + index * 0.08f),
                        Color(0xFF0B1529).copy(alpha = 0.78f)
                    ),
                    start = Offset(w * 0.28f, h * 0.15f),
                    end = Offset(w * 0.76f, h * 0.88f)
                )
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.06f + index * 0.02f),
                style = Stroke(width = 2.2f)
            )
        }
    }
}

@Composable
private fun ArcticSilkCanvas(accent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0E141F), Color(0xFF182438), Color(0xFF0B1018)),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )
        for (i in 0..4) {
            val offsetY = h * (0.18f + i * 0.14f)
            val wave = Path().apply {
                moveTo(0f, offsetY)
                cubicTo(w * 0.3f, offsetY - h * 0.14f, w * 0.65f, offsetY + h * 0.16f, w, offsetY - h * 0.06f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = wave,
                color = accent.copy(alpha = 0.06f + i * 0.025f)
            )
        }
    }
}

@Composable
private fun EmeraldAuroraCanvas() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF091214))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1F7A63).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(w * 0.32f, h * 0.38f),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w * 0.32f, h * 0.38f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF28558F).copy(alpha = 0.45f), Color.Transparent),
                center = Offset(w * 0.72f, h * 0.62f),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.72f, h * 0.62f)
        )
    }
}

@Composable
private fun ObsidianGridCanvas(accent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF0F131A))
        val step = 64f
        var x = 0f
        while (x < w) {
            drawLine(
                color = Color.White.copy(alpha = 0.035f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
            x += step
        }
        var y = 0f
        while (y < h) {
            drawLine(
                color = Color.White.copy(alpha = 0.035f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
            y += step
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.48f),
                radius = h * 0.6f
            ),
            radius = h * 0.6f,
            center = Offset(w * 0.5f, h * 0.48f)
        )
    }
}

@Composable
private fun PearlDaylightCanvas(accent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFEAF0FA), Color(0xFFDCE7F7), Color(0xFFF3F6FC)),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(w * 0.58f, h * 0.44f),
                radius = h * 0.55f
            ),
            radius = h * 0.55f,
            center = Offset(w * 0.58f, h * 0.44f)
        )
    }
}
