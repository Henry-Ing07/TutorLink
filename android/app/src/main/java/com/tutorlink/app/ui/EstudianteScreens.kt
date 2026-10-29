package com.tutorlink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tutorlink.app.R
import com.tutorlink.app.data.Aviso

/** HU-03: el estudiante ve solo sus clases. El acudiente ve las del menor a su cargo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisClasesScreen(vm: AppViewModel, padding: PaddingValues, lista: LazyListState) {
    val clases = vm.misClases
    val acudiente = vm.rol == "acudiente"
    Column(Modifier.padding(padding).fillMaxSize()) {
        BannerSinConexion(vm.sinConexion, vm::recargar)
        when {
            clases == null && vm.sinConexion -> EstadoVacio(
                R.drawable.ic_calendar, "Sin conexión",
                "Aún no hay clases descargadas en este teléfono.", "Reintentar" to vm::recargar,
            )
            clases == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(vm.cargando, vm::recargar, Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lista,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "cabecera") {
                        val proxima = clases.firstOrNull()
                        CabeceraSaludo(
                            vm.usuario?.nombre.orEmpty(),
                            when {
                                clases.isEmpty() -> "No hay clases programadas"
                                acudiente -> "${clases.size} clase(s) programada(s) de tu menor a cargo"
                                else -> "Tienes ${clases.size} clase(s) programada(s)"
                            },
                            proxima?.let {
                                "Próxima: ${Formato.diaCorto(it.inicio)} ${Formato.hora(it.inicio.toLocalTime())} · ${it.materia}"
                            },
                            Modifier.aparecer(0),
                        )
                    }
                    if (clases.isEmpty()) {
                        item(key = "vacio") {
                            Column(Modifier.height(340.dp)) {
                                EstadoVacio(
                                    R.drawable.ic_calendar,
                                    if (acudiente) "Aún no hay clases" else "Aún no tienes clases asignadas",
                                    if (acudiente) {
                                        "Cuando un docente inscriba al menor a tu cargo, la clase aparecerá aquí."
                                    } else {
                                        "Cuando tu docente te agregue a una clase, aparecerá aquí y te avisaremos."
                                    },
                                )
                            }
                        }
                    }
                    var indice = 0
                    clases.groupBy { it.inicio.toLocalDate() }.forEach { (dia, delDia) ->
                        item(key = "dia_$dia") { TituloDia(dia, Modifier.animateItem()) }
                        itemsIndexed(delDia, key = { _, c -> "${c.id}_${c.estudiante}" }) { i, clase ->
                            val detalle = if (clase.estudiante != null) {
                                "${clase.estudiante} · con ${clase.docente}"
                            } else {
                                "Con ${clase.docente}"
                            }
                            TarjetaClase(clase, detalle, Modifier.animateItem().aparecer(indice + i + 1))
                        }
                        indice += delDia.size
                    }
                }
            }
        }
    }
}

/** HU-04: bandeja de avisos de clases y avisos escritos por los docentes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvisosRecibidosScreen(vm: AppViewModel, padding: PaddingValues, lista: LazyListState) {
    Column(Modifier.padding(padding).fillMaxSize()) {
        BannerSinConexion(vm.sinConexion, vm::recargar)
        PullToRefreshBox(vm.cargando, vm::recargar, Modifier.fillMaxSize()) {
            if (vm.avisos.isEmpty() && !vm.cargando) {
                EstadoVacio(
                    R.drawable.ic_bell, "No tienes avisos",
                    "Aquí verás cuando una clase se cree, cambie de horario o se cancele, y los avisos de tus docentes.",
                )
            } else {
                LazyColumn(
                    state = lista,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(vm.avisos, key = { _, a -> a.id }) { i, aviso ->
                        TarjetaAviso(aviso, Modifier.animateItem().aparecer(i))
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaAviso(aviso: Aviso, modifier: Modifier) {
    val (icono, color) = when (aviso.tipo) {
        "anuncio" -> R.drawable.ic_megaphone to Marca.azul
        "creada" -> R.drawable.ic_calendar to Marca.turquesa
        "cambio" -> R.drawable.ic_clock to Color(0xFFE5793B)
        else -> R.drawable.ic_bell to MaterialTheme.colorScheme.error
    }
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(icono), null, Modifier.size(22.dp), tint = color) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(aviso.titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (!aviso.leida) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        Formato.hace(aviso.creadaEn), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(aviso.mensaje, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
