package com.tutorlink.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tutorlink.app.R
import com.tutorlink.app.data.Clase
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formato {
    private val es = Locale.forLanguageTag("es-CO")
    private val dia = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)
    private val diaCorto = DateTimeFormatter.ofPattern("EEE d MMM", es)
    private val hora = DateTimeFormatter.ofPattern("HH:mm")

    fun dia(fecha: LocalDate): String = fecha.format(dia).replaceFirstChar { it.titlecase(es) }
    fun diaCorto(momento: LocalDateTime): String = momento.format(diaCorto)
    fun diaCorto(fecha: LocalDate): String = fecha.format(diaCorto)
    fun hora(momento: LocalTime): String = momento.format(hora)
    fun rango(inicio: LocalDateTime, fin: LocalDateTime) =
        "${inicio.toLocalTime().format(hora)} – ${fin.toLocalTime().format(hora)}"

    fun duracion(minutos: Int) = when {
        minutos < 60 -> "$minutos min"
        minutos % 60 == 0 -> "${minutos / 60} h"
        else -> "${minutos / 60} h ${minutos % 60} min"
    }

    fun horaDe(momento: Instant): String = momento.atZone(ZoneId.systemDefault()).format(hora)

    /** "ahora", "hace 5 min", "14:20", "ayer" o "lun 26 oct". */
    fun hace(momento: Instant): String {
        val local = momento.atZone(ZoneId.systemDefault())
        val minutos = Duration.between(momento, Instant.now()).toMinutes()
        val hoy = LocalDate.now()
        return when {
            minutos < 1 -> "ahora"
            minutos < 60 -> "hace $minutos min"
            local.toLocalDate() == hoy -> local.format(hora)
            local.toLocalDate() == hoy.minusDays(1) -> "ayer"
            else -> local.toLocalDate().format(diaCorto)
        }
    }

    fun saludo(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

/** Entrada escalonada para listas: cada elemento aparece un poco después del anterior. */
fun Modifier.aparecer(indice: Int): Modifier = composed {
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(minOf(indice, 8) * 45L)
        progreso.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progreso.value
        translationY = (1f - progreso.value) * 48f
    }
}

/** Indicador de RNF-02: se muestran los últimos datos conocidos y un botón de reintento. */
@Composable
fun BannerSinConexion(visible: Boolean, onReintentar: () -> Unit) {
    AnimatedVisibility(visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sin conexión · mostrando lo último que descargaste",
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onReintentar) { Text("Reintentar") }
            }
        }
    }
}

@Composable
fun EstadoVacio(
    @DrawableRes icono: Int,
    titulo: String,
    detalle: String,
    accion: Pair<String, () -> Unit>? = null,
) {
    val flotar = rememberInfiniteTransition(label = "flotar")
    val dy by flotar.animateFloat(
        -6f, 6f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dy",
    )
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.aparecer(0)) {
            Box(
                Modifier.graphicsLayer { translationY = dy.dp.toPx() }.size(88.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icono), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (accion != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = accion.second) { Text(accion.first) }
            }
        }
    }
}

/** Cabecera con degradado de la marca, saludo y un resumen del día. */
@Composable
fun CabeceraSaludo(nombre: String, resumen: String, detalle: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(Marca.degradado),
    ) {
        Image(
            painterResource(R.drawable.logo_tutorlink), null,
            Modifier.align(Alignment.CenterEnd).offset(x = 28.dp).size(140.dp).alpha(0.16f),
        )
        Column(Modifier.padding(20.dp)) {
            Text(
                "${Formato.saludo()}, ${nombre.substringBefore(' ')}",
                style = MaterialTheme.typography.headlineSmall, color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(resumen, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
            if (detalle != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(detalle, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun Avatar(nombre: String, tamano: Int = 44) {
    val color = Marca.deTexto(nombre)
    val iniciales = nombre.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }
    Box(
        Modifier.size(tamano.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(iniciales.uppercase(), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Etiqueta(texto: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            texto, Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = color, style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Tarjeta de clase con la franja de color de la materia y su estado (en curso, hoy…). */
@Composable
fun TarjetaClase(clase: Clase, detalle: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val color = Marca.deTexto(clase.materia)
    val ahora = LocalDateTime.now()
    val estado = when {
        !ahora.isBefore(clase.inicio) && ahora.isBefore(clase.fin) -> "En curso" to MaterialTheme.colorScheme.tertiary
        clase.inicio.toLocalDate() == ahora.toLocalDate() -> "Hoy" to color
        clase.inicio.toLocalDate() == ahora.toLocalDate().plusDays(1) -> "Mañana" to color
        else -> null
    }
    val contenido: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(76.dp)) {
                    Text(Formato.hora(clase.inicio.toLocalTime()), style = MaterialTheme.typography.titleMedium)
                    Text(
                        Formato.duracion(clase.duracionMin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            clase.materia, Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (estado != null) {
                            Spacer(Modifier.width(8.dp))
                            Etiqueta(estado.first, estado.second)
                        }
                    }
                    Text(
                        detalle + if (clase.esRecurrente) " · semanal" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    val colores = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevacion = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier.fillMaxWidth(), colors = colores, elevation = elevacion) { contenido() }
    } else {
        Card(modifier.fillMaxWidth(), colors = colores, elevation = elevacion) { contenido() }
    }
}

@Composable
fun TituloDia(fecha: LocalDate, modifier: Modifier = Modifier) {
    Text(
        Formato.dia(fecha), modifier.padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SelectorFecha(fecha: LocalDate, onCambio: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                contexto,
                { _, a, m, d -> onCambio(LocalDate.of(a, m + 1, d)) },
                fecha.year, fecha.monthValue - 1, fecha.dayOfMonth,
            ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
        },
        modifier = modifier,
    ) {
        Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(Formato.diaCorto(fecha))
    }
}

@Composable
fun SelectorHora(hora: LocalTime, onCambio: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(contexto, { _, h, m -> onCambio(LocalTime.of(h, m)) },
                hora.hour, hora.minute, true).show()
        },
        modifier = modifier,
    ) {
        Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(Formato.hora(hora))
    }
}

val espacio = Arrangement.spacedBy(12.dp)

/** Pequeña sacudida horizontal para llamar la atención sobre un error. */
@Composable
fun rememberSacudida(clave: Any?): Float {
    val desplazamiento = remember { Animatable(0f) }
    LaunchedEffect(clave) {
        if (clave == null) return@LaunchedEffect
        for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) desplazamiento.animateTo(x, tween(45))
    }
    return desplazamiento.value
}
