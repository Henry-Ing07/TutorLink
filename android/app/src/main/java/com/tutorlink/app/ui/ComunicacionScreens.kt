package com.tutorlink.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tutorlink.app.R
import com.tutorlink.app.data.Contacto
import kotlinx.coroutines.delay

// ---------- Lista de conversaciones ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactosScreen(vm: AppViewModel, padding: PaddingValues, lista: LazyListState) {
    val contactos = vm.contactos
    Column(Modifier.padding(padding).fillMaxSize()) {
        BannerSinConexion(vm.sinConexion, vm::recargar)
        PullToRefreshBox(vm.cargando, vm::recargar, Modifier.fillMaxSize()) {
            if (contactos != null && contactos.isEmpty()) {
                EstadoVacio(
                    R.drawable.ic_chat, "Aún no tienes conversaciones",
                    when (vm.rol) {
                        "docente" -> "Podrás escribir a tus estudiantes mayores de edad y a los acudientes de los menores."
                        "acudiente" -> "Cuando el menor a tu cargo tenga clases, podrás escribir a sus docentes."
                        else -> "Cuando tengas clases podrás escribir a tus docentes. " +
                            "Si eres menor de edad, tu acudiente conversa con ellos."
                    },
                )
            } else {
                LazyColumn(state = lista, contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(contactos.orEmpty(), key = { _, c -> c.id }) { i, contacto ->
                        FilaContacto(contacto, Modifier.animateItem().aparecer(i)) {
                            vm.ir(Pantalla.Chat(contacto))
                        }
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaContacto(contacto: Contacto, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(contacto.nombre, 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contacto.nombre, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    contacto.ultimo?.let {
                        Text(
                            Formato.hace(it.creadoEn), style = MaterialTheme.typography.labelSmall,
                            color = if (contacto.noLeidos > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(contacto.relacion, style = MaterialTheme.typography.labelMedium, color = Marca.deTexto(contacto.nombre))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contacto.ultimo?.let { (if (it.mio) "Tú: " else "") + it.texto } ?: "Toca para escribir",
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (contacto.noLeidos > 0) {
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("${contacto.noLeidos}") }
                    }
                }
            }
        }
    }
}

// ---------- Conversación ----------

@Composable
fun ChatScreen(vm: AppViewModel, padding: PaddingValues) {
    var texto by rememberSaveable { mutableStateOf("") }
    val lista = rememberLazyListState()
    // Mientras la conversación está abierta se consultan los mensajes nuevos cada pocos segundos.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            vm.actualizarChat()
        }
    }
    LaunchedEffect(vm.chat.size) {
        if (vm.chat.isNotEmpty()) lista.animateScrollToItem(vm.chat.lastIndex)
    }
    Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
        BannerSinConexion(vm.sinConexion, vm::actualizarChat)
        LazyColumn(
            state = lista,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (vm.chat.isEmpty()) {
                item {
                    Text(
                        "Escribe el primer mensaje. Recuerda que los acuerdos de pago siguen por tu canal habitual.",
                        Modifier.fillMaxWidth().padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(vm.chat, key = { _, m -> m.id }) { _, mensaje ->
                Burbuja(mensaje.texto, Formato.horaDe(mensaje.creadoEn), mensaje.mio, Modifier.animateItem())
            }
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    texto, { if (it.length <= 1000) texto = it }, Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje") }, maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                val activo = texto.isNotBlank()
                val escala by animateFloatAsState(if (activo) 1f else 0.85f, label = "enviar")
                FilledIconButton(
                    onClick = { vm.enviarMensaje(texto) { texto = "" } },
                    enabled = activo,
                    modifier = Modifier.size(48.dp).graphicsLayer { scaleX = escala; scaleY = escala },
                ) { Icon(painterResource(R.drawable.ic_send), "Enviar") }
            }
        }
    }
}

@Composable
private fun Burbuja(texto: String, hora: String, mio: Boolean, modifier: Modifier) {
    val forma = if (mio) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }
    Box(modifier.fillMaxWidth(), contentAlignment = if (mio) Alignment.CenterEnd else Alignment.CenterStart) {
        AnimatedVisibility(true, enter = scaleIn(initialScale = 0.8f) + fadeIn()) {
            Surface(
                Modifier.widthIn(max = 300.dp), shape = forma,
                color = if (mio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(texto, color = if (mio) Color.White else MaterialTheme.colorScheme.onSurface)
                    Text(
                        hora, Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall,
                        color = if (mio) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------- Avisos que envía el docente ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvisosEnviadosScreen(vm: AppViewModel, padding: PaddingValues, lista: LazyListState) {
    Column(Modifier.padding(padding).fillMaxSize()) {
        BannerSinConexion(vm.sinConexion, vm::recargar)
        PullToRefreshBox(vm.cargando, vm::recargar, Modifier.fillMaxSize()) {
            if (vm.anuncios.isEmpty() && !vm.cargando) {
                EstadoVacio(
                    R.drawable.ic_megaphone, "Aún no has enviado avisos",
                    "Escribe a toda una clase, a algunos estudiantes o a todos. Los acudientes de los menores también lo reciben.",
                    "Nuevo aviso" to { vm.ir(Pantalla.NuevoAviso(null)) },
                )
            } else {
                LazyColumn(
                    state = lista,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(vm.anuncios, key = { _, a -> a.id }) { i, anuncio ->
                        Card(
                            Modifier.fillMaxWidth().animateItem().aparecer(i),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(painterResource(R.drawable.ic_megaphone), null, Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(anuncio.titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    Text(Formato.hace(anuncio.creadoEn), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(anuncio.mensaje, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    anuncio.materia?.let { Etiqueta(it, Marca.deTexto(it)) }
                                    Etiqueta("${anuncio.destinatarios} destinatario(s)", Marca.azulOscuro)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NuevoAvisoScreen(vm: AppViewModel, padding: PaddingValues) {
    val f = vm.formAviso
    Column(
        Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = espacio,
    ) {
        Text("¿A quién va dirigido?", Modifier.aparecer(0), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.aparecer(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Destino.TODOS to "Todos", Destino.CLASE to "Una clase", Destino.ESTUDIANTES to "Algunos")
                .forEach { (destino, texto) ->
                    val fondo by animateColorAsState(
                        if (f.destino == destino) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        label = "destino",
                    )
                    Surface(
                        onClick = { vm.editarAviso { copy(destino = destino) } },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(50), color = fondo,
                    ) {
                        Text(
                            texto, Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            color = if (f.destino == destino) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
        }
        AnimatedVisibility(f.destino == Destino.CLASE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val clases = (listOfNotNull(f.clase) + vm.proximas).distinctBy { it.id }
                if (clases.isEmpty()) {
                    Text("No tienes clases próximas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                clases.take(12).forEach { clase ->
                    val elegida = f.clase?.id == clase.id
                    FilterChip(
                        elegida, { vm.editarAviso { copy(clase = clase) } },
                        label = {
                            Text("${clase.materia} · ${Formato.diaCorto(clase.inicio)} ${Formato.hora(clase.inicio.toLocalTime())}")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AnimatedVisibility(f.destino == Destino.ESTUDIANTES) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.conocidos.isEmpty()) {
                    Text("Aún no tienes estudiantes en tus clases.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                vm.conocidos.forEach { e ->
                    val marcado = f.estudiantes.any { it.id == e.id }
                    FilterChip(marcado, {
                        vm.editarAviso {
                            copy(estudiantes = if (marcado) estudiantes.filterNot { it.id == e.id } else estudiantes + e)
                        }
                    }, label = { Text(e.nombre) })
                }
            }
        }
        OutlinedTextField(
            f.titulo, { if (it.length <= 80) vm.editarAviso { copy(titulo = it) } },
            Modifier.fillMaxWidth().aparecer(1), label = { Text("Título") }, singleLine = true,
            supportingText = { Text("${f.titulo.length}/80") }, shape = MaterialTheme.shapes.medium,
        )
        OutlinedTextField(
            f.mensaje, { if (it.length <= 400) vm.editarAviso { copy(mensaje = it) } },
            Modifier.fillMaxWidth().height(160.dp).aparecer(2), label = { Text("Mensaje") },
            supportingText = { Text("${f.mensaje.length}/400 · los acudientes de los menores también lo reciben") },
            shape = MaterialTheme.shapes.medium,
        )
        AnimatedVisibility(f.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val sacudida = rememberSacudida(f.error)
            Surface(
                Modifier.fillMaxWidth().graphicsLayer { translationX = sacudida },
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(f.error.orEmpty(), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Button(onClick = vm::enviarAviso, enabled = !f.enviando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(painterResource(R.drawable.ic_send), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (f.enviando) "Enviando…" else "Enviar aviso", style = MaterialTheme.typography.titleMedium)
        }
    }
}
