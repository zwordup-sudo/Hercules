package com.rafa.hercules.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Sistema visual de Hércules (v1.15). Paletas tomadas de las referencias aprobadas:
 * - Claro "panel celeste": fondo #E9F6FE, tarjetas blancas, texto azul marino, acentos azules.
 * - Oscuro "grafito y violeta": fondo #15181D, tarjetas #1F232B, texto claro, acentos violetas.
 * Colores dinámicos del sistema desactivados a propósito: las paletas aprobadas mandan.
 */

// ——— Claro ———
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B66FE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF001F44),
    secondary = Color(0xFF4C6793),
    onSecondary = Color(0xFFFFFFFF),
    // Indicador de navegación seleccionado: celeste/azul claro.
    secondaryContainer = Color(0xFFD3E6FF),
    onSecondaryContainer = Color(0xFF0A1E3D),
    tertiary = Color(0xFF3D5FA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE7FB),
    onTertiaryContainer = Color(0xFF0E2350),
    background = Color(0xFFE9F6FE),
    onBackground = Color(0xFF0E1B3D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1B3D),
    surfaceVariant = Color(0xFFE3EEFB),
    onSurfaceVariant = Color(0xFF3F5470),
    surfaceTint = Color(0xFF0B66FE),
    inverseSurface = Color(0xFF2C313A),
    inverseOnSurface = Color(0xFFEEF1F5),
    inversePrimary = Color(0xFF0B66FE),
    // Contenedores de superficie: tarjetas, navegación y diálogos usan estas, no la línea base M3.
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD7E4F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5FAFF),
    surfaceContainer = Color(0xFFEAF4FE),
    surfaceContainerHigh = Color(0xFFE1EEFA),
    surfaceContainerHighest = Color(0xFFD8E8F7),
    outline = Color(0xFFB7C7DE),
    outlineVariant = Color(0xFFDCE7F3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DED9),
    onErrorContainer = Color(0xFF410002)
)

// ——— Oscuro ———
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9364FB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3A2B69),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFA8B8D6),
    onSecondary = Color(0xFF10203A),
    // Indicador de navegación seleccionado: violeta suave (no el azul por defecto).
    secondaryContainer = Color(0xFF3A2E63),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFFC8B0FF),
    onTertiary = Color(0xFF2A1A55),
    tertiaryContainer = Color(0xFF413467),
    onTertiaryContainer = Color(0xFFEEDFFF),
    background = Color(0xFF15181D),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF1F232B),
    onSurface = Color(0xFFE7E9EE),
    surfaceVariant = Color(0xFF2A2F3A),
    onSurfaceVariant = Color(0xFFB7BDC9),
    surfaceTint = Color(0xFF9364FB),
    inverseSurface = Color(0xFFE3E6EF),
    inverseOnSurface = Color(0xFF2B3038),
    inversePrimary = Color(0xFF0B66FE),
    // Grafito coherente para tarjetas, barra de navegación y diálogos.
    // Sin esto, darkColorScheme() usa la paleta base M3 (tonos pardos).
    surfaceBright = Color(0xFF343A44),
    surfaceDim = Color(0xFF0F1216),
    surfaceContainerLowest = Color(0xFF0A0C10),
    surfaceContainerLow = Color(0xFF171B21),
    surfaceContainer = Color(0xFF1C2028),
    surfaceContainerHigh = Color(0xFF232831),
    surfaceContainerHighest = Color(0xFF2C323C),
    outline = Color(0xFF454B57),
    outlineVariant = Color(0xFF2E333D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** Jerarquía tipográfica: base del sistema + ajustes de título/lectura. Respeta fuente ampliada. */
val HerculesTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
)

/** Formas compartidas: tarjetas y superficies redondeadas de ambas referencias. */
val HerculesShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Espaciados y medidas táctiles en un solo lugar. */
object HerculesSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    /** Tamaño mínimo de control táctil. */
    val touch = 48.dp
}

/**
 * Tema global de la app. Se usa en toda la aplicación; el modo lo decide
 * Ajustes (Claro/Oscuro/Sistema) y se aplica en MainActivity.
 */
@Composable
fun HerculesTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Colores dinámicos desactivados: conserva las paletas aprobadas en cualquier dispositivo.
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HerculesTypography,
        shapes = HerculesShapes,
        content = content
    )
}
