package com.tutorlink.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tutorlink.app.data.Clase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
}

/** Indicador de RNF-02: se muestran los últimos datos conocidos y un botón de reintento. */
@Composable
fun BannerSinConexion(onReintentar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
fun EstadoVacio(titulo: String, detalle: String, accion: Pair<String, () -> Unit>? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (accion != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = accion.second) { Text(accion.first) }
            }
        }
    }
}

@Composable
fun TarjetaClase(clase: Clase, mostrarDocente: Boolean, onClick: (() -> Unit)? = null) {
    val contenido: @Composable () -> Unit = {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(84.dp)) {
                Text(Formato.hora(clase.inicio.toLocalTime()), fontWeight = FontWeight.Bold)
                Text(
                    Formato.duracion(clase.duracionMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(clase.materia, style = MaterialTheme.typography.titleMedium)
                val detalle = if (mostrarDocente) {
                    "Con ${clase.docente}"
                } else {
                    clase.estudiantes.joinToString { it.nombre }.ifEmpty { "Sin estudiantes" }
                }
                Text(
                    detalle + if (clase.esRecurrente) " · semanal" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val colores = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(onClick = onClick, Modifier.fillMaxWidth(), colors = colores) { contenido() }
    } else {
        Card(Modifier.fillMaxWidth(), colors = colores) { contenido() }
    }
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
    ) { Text(Formato.diaCorto(fecha)) }
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
    ) { Text(Formato.hora(hora)) }
}

val espacio = Arrangement.spacedBy(12.dp)
