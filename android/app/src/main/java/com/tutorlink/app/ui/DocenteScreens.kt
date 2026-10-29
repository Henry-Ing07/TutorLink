package com.tutorlink.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tutorlink.app.R
import com.tutorlink.app.data.Clase

private val DURACIONES = listOf(30, 45, 60, 90, 120, 180)
private val SEMANAS = listOf(4, 8, 12, 16)

/** La pantalla más usada por el docente: su semana de un vistazo (observación FPI-04). */
@Composable
fun AgendaScreen(vm: AppViewModel, padding: PaddingValues) {
    val dias = (0L..6L).map { vm.semana.plusDays(it) }
    val porDia = vm.agenda.groupBy { it.inicio.toLocalDate() }
    Column(Modifier.padding(padding).fillMaxSize()) {
        if (vm.sinConexion) BannerSinConexion(vm::recargar)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.cambiarSemana(-1) }) {
                Icon(painterResource(R.drawable.ic_left), "Semana anterior")
            }
            Text(
                "${Formato.diaCorto(dias.first())} – ${Formato.diaCorto(dias.last())}",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { vm.cambiarSemana(1) }) {
                Icon(painterResource(R.drawable.ic_right), "Semana siguiente")
            }
        }
        if (vm.agenda.isEmpty() && !vm.cargando) {
            EstadoVacio(
                "No tienes clases esta semana",
                "Crea una clase y agrega a tus estudiantes; ellos la verán en su app.",
                "Nueva clase" to { vm.ir(Pantalla.FormClase(null)) },
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dias.filter { it in porDia }.forEach { dia ->
                item(key = dia.toString()) {
                    Text(
                        Formato.dia(dia), Modifier.padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(porDia.getValue(dia), key = { it.id }) { clase ->
                    TarjetaClase(clase, mostrarDocente = false) { vm.ir(Pantalla.Detalle(clase)) }
                }
            }
        }
    }
}

@Composable
fun DetalleClaseScreen(vm: AppViewModel, clase: Clase, padding: PaddingValues) {
    var preguntarCancelar by remember { mutableStateOf(false) }
    Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = espacio) {
        Text(clase.materia, style = MaterialTheme.typography.headlineSmall)
        Text("${Formato.dia(clase.inicio.toLocalDate())}\n${Formato.rango(clase.inicio, clase.fin)} · " +
            Formato.duracion(clase.duracionMin))
        if (clase.esRecurrente) {
            Text("Forma parte de una franja semanal", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Estudiantes", style = MaterialTheme.typography.titleSmall)
        clase.estudiantes.forEach { Text("• ${it.nombre}") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.ir(Pantalla.FormClase(clase)) }, Modifier.fillMaxWidth()) { Text("Reagendar o editar") }
        OutlinedButton(onClick = { preguntarCancelar = true }, Modifier.fillMaxWidth()) { Text("Cancelar clase") }
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
            f.materia, { v -> vm.editar { copy(materia = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Materia") }, singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectorFecha(f.fecha, { v -> vm.editar { copy(fecha = v) } }, Modifier.weight(1f))
            SelectorHora(f.hora, { v -> vm.editar { copy(hora = v) } }, Modifier.weight(1f))
        }
        Text("Duración", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURACIONES.forEach { d ->
                FilterChip(f.duracionMin == d, { vm.editar { copy(duracionMin = d) } },
                    label = { Text(Formato.duracion(d)) })
            }
        }

        Text("Estudiantes", style = MaterialTheme.typography.titleSmall)
        val opciones = (vm.conocidos + f.estudiantes).distinctBy { it.id }
        if (opciones.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                correo, { correo = it }, Modifier.weight(1f), label = { Text("Agregar por correo") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            TextButton(onClick = { vm.agregarPorCorreo(correo) { correo = "" } }, enabled = correo.isNotBlank()) {
                Text("Agregar")
            }
        }

        if (editando == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Repetir cada semana", Modifier.weight(1f))
                Switch(f.repetir, { v -> vm.editar { copy(repetir = v) } })
            }
            if (f.repetir) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SEMANAS.forEach { s ->
                        FilterChip(f.semanas == s, { vm.editar { copy(semanas = s) } },
                            label = { Text("$s semanas") })
                    }
                }
            }
        }

        f.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (editando?.esRecurrente == true) preguntarAlcance = true else vm.guardarClase(editando)
            },
            enabled = !f.guardando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (f.guardando) "Guardando…" else "Guardar") }
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
