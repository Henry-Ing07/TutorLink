package com.tutorlink.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tutorlink.app.R
import com.tutorlink.app.data.Clase
import java.time.LocalDate
import java.time.LocalDateTime

private val DURACIONES = listOf(30, 45, 60, 90, 120, 180)
private val SEMANAS = listOf(4, 8, 12, 16)

/** La pantalla más usada por el docente: su semana de un vistazo (observación FPI-04). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(vm: AppViewModel, padding: PaddingValues, lista: LazyListState) {
    val dias = (0L..6L).map { vm.semana.plusDays(it) }
    val porDia = vm.agenda.groupBy { it.inicio.toLocalDate() }
    val hoy = vm.proximas.filter { it.inicio.toLocalDate() == LocalDate.now() }
    val siguiente = vm.proximas.firstOrNull { it.fin.isAfter(LocalDateTime.now()) }

    Column(Modifier.padding(padding).fillMaxSize()) {
        BannerSinConexion(vm.sinConexion, vm::recargar)
        PullToRefreshBox(vm.cargando, vm::recargar, Modifier.fillMaxSize()) {
            LazyColumn(
                state = lista,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "cabecera") {
                    CabeceraSaludo(
                        vm.usuario?.nombre.orEmpty(),
                        when (hoy.size) {
                            0 -> "Hoy no tienes clases"
                            1 -> "Hoy tienes 1 clase"
                            else -> "Hoy tienes ${hoy.size} clases"
                        },
                        siguiente?.let {
                            "Próxima: ${Formato.diaCorto(it.inicio)} ${Formato.hora(it.inicio.toLocalTime())} · ${it.materia}"
                        },
                        Modifier.aparecer(0),
                    )
                }
                item(key = "semana") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { vm.cambiarSemana(-1) }) {
                            Icon(painterResource(R.drawable.ic_left), "Semana anterior")
                        }
                        Text(
                            "${Formato.diaCorto(dias.first())} – ${Formato.diaCorto(dias.last())}",
                            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        FilledTonalIconButton(onClick = { vm.cambiarSemana(1) }) {
                            Icon(painterResource(R.drawable.ic_right), "Semana siguiente")
                        }
                    }
                }
                if (vm.agenda.isEmpty() && !vm.cargando) {
                    item(key = "vacio") {
                        Column(Modifier.height(320.dp)) {
                            EstadoVacio(
                                R.drawable.ic_calendar,
                                "No tienes clases esta semana",
                                "Crea una clase y agrega a tus estudiantes; ellos la verán en su app.",
                            )
                        }
                    }
                }
                var indice = 0
                dias.filter { it in porDia }.forEach { dia ->
                    item(key = "dia_$dia") { TituloDia(dia, Modifier.animateItem()) }
                    val delDia = porDia.getValue(dia)
                    itemsIndexed(delDia, key = { _, c -> c.id }) { i, clase ->
                        TarjetaClase(
                            clase,
                            clase.estudiantes.joinToString { it.nombre }.ifEmpty { "Sin estudiantes" },
                            Modifier.animateItem().aparecer(indice + i),
                        ) { vm.ir(Pantalla.Detalle(clase)) }
                    }
                    indice += delDia.size
                }
            }
        }
    }
}

@Composable
fun DetalleClaseScreen(vm: AppViewModel, clase: Clase, padding: PaddingValues) {
    var preguntarCancelar by remember { mutableStateOf(false) }
    val color = Marca.deTexto(clase.materia)
    Column(
        Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = espacio,
    ) {
        Card(
            Modifier.fillMaxWidth().aparecer(0),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(clase.materia, style = MaterialTheme.typography.headlineSmall, color = color)
                Spacer(Modifier.height(8.dp))
                Text(Formato.dia(clase.inicio.toLocalDate()), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Formato.rango(clase.inicio, clase.fin)} · ${Formato.duracion(clase.duracionMin)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (clase.esRecurrente) {
                    Spacer(Modifier.height(8.dp))
                    Etiqueta("Franja semanal", color)
                }
            }
        }
        Text("Estudiantes (${clase.estudiantes.size})", Modifier.aparecer(1), style = MaterialTheme.typography.titleSmall)
        clase.estudiantes.forEachIndexed { i, e ->
            Row(Modifier.aparecer(i + 2), verticalAlignment = Alignment.CenterVertically) {
                Avatar(e.nombre, 36)
                Spacer(Modifier.width(12.dp))
                Text(e.nombre, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.ir(Pantalla.FormClase(clase)) }, Modifier.fillMaxWidth().height(48.dp)) {
            Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reagendar o editar")
        }
        OutlinedButton(onClick = { vm.ir(Pantalla.NuevoAviso(clase)) }, Modifier.fillMaxWidth().height(48.dp)) {
            Icon(painterResource(R.drawable.ic_megaphone), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Enviar aviso a esta clase")
        }
        TextButton(onClick = { preguntarCancelar = true }, Modifier.fillMaxWidth()) {
            Text("Cancelar clase", color = MaterialTheme.colorScheme.error)
        }
    }
    if (preguntarCancelar) {
        DialogoAlcance(
            titulo = "¿Cancelar ${clase.materia}?",
            texto = "Los estudiantes inscritos recibirán un aviso.",
            recurrente = clase.esRecurrente,
            confirmar = "Cancelar clase",
            onElegir = { alcance -> preguntarCancelar = false; vm.cancelarClase(clase, alcance) },
            onCerrar = { preguntarCancelar = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormClaseScreen(vm: AppViewModel, editando: Clase?, padding: PaddingValues) {
    val f = vm.formulario
    var correo by remember { mutableStateOf("") }
    var preguntarAlcance by remember { mutableStateOf(false) }

    Column(
        Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = espacio,
    ) {
        OutlinedTextField(
            f.materia, { v -> vm.editar { copy(materia = v) } }, Modifier.fillMaxWidth().aparecer(0),
            label = { Text("Materia") }, singleLine = true, shape = MaterialTheme.shapes.medium,
        )
        Row(Modifier.aparecer(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectorFecha(f.fecha, { v -> vm.editar { copy(fecha = v) } }, Modifier.weight(1f))
            SelectorHora(f.hora, { v -> vm.editar { copy(hora = v) } }, Modifier.weight(1f))
        }
        Text("Duración", Modifier.aparecer(2), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.aparecer(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURACIONES.forEach { d ->
                FilterChip(f.duracionMin == d, { vm.editar { copy(duracionMin = d) } },
                    label = { Text(Formato.duracion(d)) })
            }
        }

        Text("Estudiantes", Modifier.aparecer(3), style = MaterialTheme.typography.titleSmall)
        val opciones = (vm.conocidos + f.estudiantes).distinctBy { it.id }
        if (opciones.isNotEmpty()) {
            FlowRow(Modifier.aparecer(3), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opciones.forEach { e ->
                    val marcado = f.estudiantes.any { it.id == e.id }
                    FilterChip(marcado, {
                        vm.editar {
                            copy(estudiantes = if (marcado) estudiantes.filterNot { it.id == e.id } else estudiantes + e)
                        }
                    }, label = { Text(e.nombre) })
                }
            }
        }
        Row(Modifier.aparecer(4), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                correo, { correo = it }, Modifier.weight(1f), label = { Text("Agregar por correo") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = MaterialTheme.shapes.medium,
            )
            TextButton(onClick = { vm.agregarPorCorreo(correo) { correo = "" } }, enabled = correo.isNotBlank()) {
                Text("Agregar")
            }
        }

        if (editando == null) {
            Surface(Modifier.aparecer(5), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Repetir cada semana", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Mismo día y hora, por varias semanas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(f.repetir, { v -> vm.editar { copy(repetir = v) } })
                    }
                    AnimatedVisibility(f.repetir) {
                        FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SEMANAS.forEach { s ->
                                FilterChip(f.semanas == s, { vm.editar { copy(semanas = s) } },
                                    label = { Text("$s semanas") })
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(f.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val sacudida = rememberSacudida(f.error)
            Surface(
                Modifier.fillMaxWidth().padding(top = 4.dp).graphicsLayer { translationX = sacudida },
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(f.error.orEmpty(), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Button(
            onClick = {
                if (editando?.esRecurrente == true) preguntarAlcance = true else vm.guardarClase(editando)
            },
            enabled = !f.guardando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (f.guardando) "Guardando…" else "Guardar", style = MaterialTheme.typography.titleMedium) }
    }

    if (preguntarAlcance && editando != null) {
        DialogoAlcance(
            titulo = "¿Qué clases cambian?",
            texto = "Esta clase pertenece a una franja semanal.",
            recurrente = true,
            confirmar = "Guardar",
            onElegir = { alcance -> preguntarAlcance = false; vm.guardarClase(editando, alcance) },
            onCerrar = { preguntarAlcance = false },
        )
    }
}

/** Paso 3 del caso de uso «Reagendar»: solo esta ocurrencia o también las siguientes. */
@Composable
private fun DialogoAlcance(
    titulo: String,
    texto: String,
    recurrente: Boolean,
    confirmar: String,
    onElegir: (String) -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            if (recurrente) {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onElegir("ocurrencia") }) { Text("Solo esta clase") }
                    TextButton(onClick = { onElegir("serie") }) { Text("Esta y las siguientes") }
                }
            } else {
                TextButton(onClick = { onElegir("ocurrencia") }) { Text(confirmar) }
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Volver") } },
    )
}
