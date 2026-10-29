package com.tutorlink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** HU-03: el estudiante ve solo las clases a las que fue inscrito. */
@Composable
fun MisClasesScreen(vm: AppViewModel, padding: PaddingValues) {
    val clases = vm.misClases
    Column(Modifier.padding(padding).fillMaxSize()) {
        if (vm.sinConexion) BannerSinConexion(vm::recargar)
        when {
            clases == null && vm.sinConexion -> EstadoVacio(
                "Sin conexión",
                "Aún no hay clases descargadas en este teléfono.",
                "Reintentar" to vm::recargar,
            )
            clases == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            clases.isEmpty() -> EstadoVacio(
                "Aún no tienes clases asignadas",
                "Cuando tu docente te agregue a una clase, aparecerá aquí y te avisaremos.",
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clases.groupBy { it.inicio.toLocalDate() }.forEach { (dia, delDia) ->
                    item(key = dia.toString()) {
                        Text(
                            Formato.dia(dia), Modifier.padding(top = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(delDia, key = { it.id }) { TarjetaClase(it, mostrarDocente = true) }
                }
            }
        }
    }
}

/** HU-04: bandeja de avisos para estudiantes y acudientes. */
@Composable
fun AvisosScreen(vm: AppViewModel, padding: PaddingValues) {
    Column(Modifier.padding(padding).fillMaxSize()) {
        if (vm.sinConexion) BannerSinConexion(vm::recargar)
        if (vm.avisos.isEmpty() && !vm.cargando) {
            EstadoVacio(
                "No tienes avisos",
                "Aquí verás cuando una clase se cree, cambie de horario o se cancele.",
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.avisos, key = { it.id }) { aviso ->
                val color = if (aviso.tipo == "cancelada" || aviso.tipo == "retirado") {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                        Text(aviso.mensaje, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
