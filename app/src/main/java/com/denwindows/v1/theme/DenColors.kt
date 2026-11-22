package com.denwindows.v1.theme

import androidx.compose.ui.graphics.Color

enum class AccentOption(
    val id: String,
    val label: String,
    val color: Color,
    val containerDark: Color,
    val containerLight: Color
) {
    COBALT("cobalt", "Cobalt Blue", Color(0xFF4C8DFF), Color(0xFF1E345A), Color(0xFFD8E6FF)),
    EMERALD("emerald", "Emerald", Color(0xFF2FB37E), Color(0xFF173D31), Color(0xFFD1F2E4)),
    VIOLET("violet", "Iris Violet", Color(0xFF7C6DFF), Color(0xFF292459), Color(0xFFE3E0FF)),
    AMBER("amber", "Warm Amber", Color(0xFFE69B3A), Color(0xFF473116), Color(0xFFFFE8C7)),
    ROSE("rose", "Crimson Rose", Color(0xFFE15B78), Color(0xFF4B1E2A), Color(0xFFFFD9E1)),
    TEAL("teal", "Ocean Teal", Color(0xFF26A69A), Color(0xFF133A36), Color(0xFFCEEFEA));

    companion object {
        fun fromId(id: String): AccentOption = entries.firstOrNull { it.id == id } ?: COBALT
    }
}

object DenPalette {
    // Dark Desktop Tokens (Windows 11++ Spec)
    val DarkBackground = Color(0xFF101318)
    val DarkSurface = Color(0xFF1A2029)
    val DarkSecondarySurface = Color(0xFF242C38)
    val DarkGlassSurface = Color(0xE61A2029)
    val DarkTaskbarSurface = Color(0xEB141921)
    val DarkTextPrimary = Color(0xFFF5F7FA)
    val DarkTextSecondary = Color(0xFFA7B0BE)
    val DarkBorder = Color(0xFF354052)

    // Light Desktop Tokens
    val LightBackground = Color(0xFFF2F5FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSecondarySurface = Color(0xFFE8EEF7)
    val LightGlassSurface = Color(0xEBFFFFFF)
    val LightTaskbarSurface = Color(0xECF3F6FB)
    val LightTextPrimary = Color(0xFF141820)
    val LightTextSecondary = Color(0xFF566070)
    val LightBorder = Color(0xFFD0D8E4)

    // Semantic Status
    val Success = Color(0xFF38C976)
    val Warning = Color(0xFFF0B449)
    val Error = Color(0xFFE85A6B)
}
