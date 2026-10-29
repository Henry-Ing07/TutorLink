package com.tutorlink.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Colores tomados del logo de TutorLink. */
object Marca {
    val azul = Color(0xFF2B6FD6)
    val azulOscuro = Color(0xFF1A3C7E)
    val azulClaro = Color(0xFF5B9BF0)
    val turquesa = Color(0xFF00A389)
    val fondo = Color(0xFFF4F7FC)

    val degradado = Brush.linearGradient(listOf(azulOscuro, azul, azulClaro))

    /** Color estable por materia, para reconocer cada clase de un vistazo. */
    private val paleta = listOf(
        Color(0xFF2B6FD6), Color(0xFF00A389), Color(0xFFE5793B), Color(0xFF8E5BD8),
        Color(0xFFD64B7A), Color(0xFF2A9BB8), Color(0xFFB8912A),
    )

    fun deTexto(texto: String) = paleta[Math.floorMod(texto.trim().lowercase().hashCode(), paleta.size)]
}

private val colores = lightColorScheme(
    primary = Marca.azul,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FB),
    onPrimaryContainer = Marca.azulOscuro,
    secondary = Marca.azulOscuro,
    secondaryContainer = Color(0xFFE3E9F6),
    onSecondaryContainer = Marca.azulOscuro,
    tertiary = Marca.turquesa,
    background = Marca.fondo,
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2FA),
    surfaceContainer = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF5A6478),
    outlineVariant = Color(0xFFDDE3EE),
)

private val tipografia = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val formas = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TemaTutorLink(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colores, typography = tipografia, shapes = formas, content = contenido)
}
