package com.shiyi.memory.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Space {
    val small = 8.dp
    val item = 12.dp
    val page = 20.dp
    val section = 24.dp
}

val ShiYiTypography =
    Typography(
        headlineLarge =
            TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium =
            TextStyle(fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall =
            TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        titleLarge =
            TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        titleMedium =
            TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
        bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge =
            TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    )

@androidx.compose.runtime.Composable
fun ShiYiTheme(theme: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFFE8B5C6),
                secondary = Color(0xFFC7BAE7),
                background = Color(0xFF19181D),
                surface = Color(0xFF252329),
                surfaceVariant = Color(0xFF34313A),
                onBackground = Color(0xFFF3EDF0),
                onSurface = Color(0xFFF3EDF0),
            )
        else
            lightColorScheme(
                primary = Color(0xFF92506A),
                primaryContainer = Color(0xFFF2DDE5),
                secondary = Color(0xFF776394),
                secondaryContainer = Color(0xFFECE5F7),
                background = Color(0xFFFAF8F5),
                surface = Color.White,
                surfaceVariant = Color(0xFFEFEAE7),
                onBackground = Color(0xFF34323A),
                onSurface = Color(0xFF34323A),
                onSurfaceVariant = Color(0xFF6C6873),
                outline = Color(0xFF92909A),
            )
    MaterialTheme(
        colorScheme = colors,
        typography = ShiYiTypography,
        shapes = Shapes(medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)),
    ) {
        content()
    }
}
