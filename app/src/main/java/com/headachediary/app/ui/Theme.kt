package com.headachediary.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun HeadacheTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Цвет по силе боли: жёлтый — слабая, оранжевый — средняя, красный — сильная. */
fun painColor(intensity: Int?): Color = when {
    intensity == null -> Color(0xFF8E8E93)
    intensity <= 3 -> Color(0xFFF9A825)
    intensity <= 6 -> Color(0xFFEF6C00)
    else -> Color(0xFFD32F2F)
}

fun onPainColor(intensity: Int?): Color =
    if (intensity != null && intensity <= 3) Color.Black else Color.White
