package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumLightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),        // Indigo
    secondary = Color(0xFF0D9488),      // Teal
    tertiary = Color(0xFFEA580C),       // Orange/Amber
    background = Color(0xFFF8FAFC),     // Bright Slate / Ice white
    surface = Color(0xFFFFFFFF),        // Card White
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),   // Dark Navy / Slate Slate
    onSurface = Color(0xFF1E293B),      // Navy / Slate
    surfaceVariant = Color(0xFFF1F5F9), // Light variant
    onSurfaceVariant = Color(0xFF475569), // Muted text
    outline = Color(0xFFE2E8F0),        // Soft slate outline for cards
    outlineVariant = Color(0xFFCBD5E1)
)

private val PremiumDarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),        // Light Indigo
    secondary = Color(0xFF2DD4BF),      // Light Teal
    tertiary = Color(0xFFF97316),       // Vibrant Orange
    background = Color(0xFF0F172A),     // Dark Slate (Slate-900)
    surface = Color(0xFF1E293B),        // Dark Card Slate (Slate-800)
    onPrimary = Color(0xFF0F172A),
    onSecondary = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),   // Ice white
    onSurface = Color(0xFFF8FAFC),      // Ice white
    surfaceVariant = Color(0xFF334155), // Mid Slate
    onSurfaceVariant = Color(0xFF94A3B8), // Muted text
    outline = Color(0xFF475569),        // Soft dark outline
    outlineVariant = Color(0xFF334155)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, 
    dynamicColor: Boolean = false, 
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) PremiumDarkColorScheme else PremiumLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
