package com.tutorlink.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tutorlink.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorLinkApp(vm: AppViewModel) {
    TemaTutorLink {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.mensaje) {
            vm.mensaje?.let {
                snackbar.showSnackbar(it)
                vm.mensaje = null
            }
        }
        BackHandler(enabled = vm.puedeVolver) { vm.volver() }

        // Transición entre pantallas: se desliza hacia adelante o hacia atrás según la navegación.
        AnimatedContent(
            targetState = vm.pantalla,
            contentKey = { it::class },
            transitionSpec = {
                if (initialState == Pantalla.Acceso || targetState == Pantalla.Acceso) {
                    (fadeIn(tween(400)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(250))
                } else {
                    val dir = if (vm.avanzando) 1 else -1
                    (slideInHorizontally(tween(320)) { it * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(320)) { -it * dir / 3 } + fadeOut())
                }
            },
            label = "pantalla",
        ) { pantalla ->
            if (pantalla == Pantalla.Acceso) {
                AccesoScreen(vm)
            } else {
                Marco(vm, pantalla, snackbar)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Marco(vm: AppViewModel, pantalla: Pantalla, snackbar: SnackbarHostState) {
    val listas = mapOf(
        Pestana.CLASES to rememberLazyListState(),
        Pestana.MENSAJES to rememberLazyListState(),
        Pestana.AVISOS to rememberLazyListState(),
    )
    val lista = listas.getValue(vm.pestana)
    val expandido by remember(lista) { derivedStateOf { lista.firstVisibleItemIndex == 0 } }
    val principal = pantalla == Pantalla.Principal
    val docente = vm.rol == "docente"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Titulo(vm, pantalla) },
                navigationIcon = {
                    if (vm.puedeVolver) {
                        IconButton(onClick = vm::volver) { Icon(painterResource(R.drawable.ic_back), "Volver") }
                    }
                },
                actions = {
                    if (principal) {
                        IconButton(onClick = vm::salir) { Icon(painterResource(R.drawable.ic_logout), "Cerrar sesión") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                principal,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) { BarraNavegacion(vm) }
        },
        floatingActionButton = {
            val fab = when {
                !principal || !docente -> null
                vm.pestana == Pestana.CLASES -> Triple(R.drawable.ic_add, "Nueva clase") { vm.ir(Pantalla.FormClase(null)) }
                vm.pestana == Pestana.AVISOS -> Triple(R.drawable.ic_megaphone, "Nuevo aviso") { vm.ir(Pantalla.NuevoAviso(null)) }
                else -> null
            }
            AnimatedVisibility(fab != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                if (fab != null) {
                    ExtendedFloatingActionButton(
                        onClick = fab.third,
                        expanded = expandido,
                        icon = { Icon(painterResource(fab.first), null) },
                        text = { Text(fab.second) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (pantalla) {
            Pantalla.Principal -> AnimatedContent(
                vm.pestana,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(280)) { it * dir / 4 } + fadeIn(tween(280))) togetherWith
                        fadeOut(tween(160))
                },
                label = "pestana",
            ) { pestana -> ContenidoPestana(vm, pestana, padding, listas.getValue(pestana)) }
            is Pantalla.FormClase -> FormClaseScreen(vm, pantalla.editando, padding)
            is Pantalla.Detalle -> DetalleClaseScreen(vm, pantalla.clase, padding)
            is Pantalla.Chat -> ChatScreen(vm, padding)
            is Pantalla.NuevoAviso -> NuevoAvisoScreen(vm, padding)
            Pantalla.Acceso -> Unit
        }
    }
}

@Composable
private fun ContenidoPestana(vm: AppViewModel, pestana: Pestana, padding: PaddingValues, lista: LazyListState) {
    when (pestana) {
        Pestana.CLASES -> if (vm.rol == "docente") AgendaScreen(vm, padding, lista) else MisClasesScreen(vm, padding, lista)
        Pestana.MENSAJES -> ContactosScreen(vm, padding, lista)
        Pestana.AVISOS -> if (vm.rol == "docente") AvisosEnviadosScreen(vm, padding, lista) else AvisosRecibidosScreen(vm, padding, lista)
    }
}

@Composable
private fun BarraNavegacion(vm: AppViewModel) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        val clases = when (vm.rol) {
            "docente" -> "Mi semana"
            "acudiente" -> "Clases"
            else -> "Mis clases"
        }
        Item(vm, Pestana.CLASES, R.drawable.ic_calendar, clases, 0)
        Item(vm, Pestana.MENSAJES, R.drawable.ic_chat, "Mensajes", vm.mensajesSinLeer)
        Item(vm, Pestana.AVISOS, if (vm.rol == "docente") R.drawable.ic_megaphone else R.drawable.ic_bell,
            "Avisos", if (vm.rol == "docente") 0 else vm.avisosSinLeer)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Item(
    vm: AppViewModel,
    pestana: Pestana,
    @DrawableRes icono: Int,
    texto: String,
    pendientes: Int,
) {
    NavigationBarItem(
        selected = vm.pestana == pestana,
        onClick = { vm.elegirPestana(pestana) },
        icon = {
            BadgedBox(badge = {
                if (pendientes > 0) Badge { Text(if (pendientes > 9) "9+" else "$pendientes") }
            }) { Icon(painterResource(icono), null) }
        },
        label = { Text(texto) },
    )
}

@Composable
private fun Titulo(vm: AppViewModel, pantalla: Pantalla) {
    when (pantalla) {
        is Pantalla.Chat -> Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(pantalla.contacto.nombre, 38)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(pantalla.contacto.nombre, style = MaterialTheme.typography.titleMedium)
                Text(
                    pantalla.contacto.relacion, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Pantalla.Principal -> Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_tutorlink), null, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            AnimatedContent(vm.pestana, label = "titulo") { pestana ->
                Text(
                    when (pestana) {
                        Pestana.CLASES -> "TutorLink"
                        Pestana.MENSAJES -> "Mensajes"
                        Pestana.AVISOS -> if (vm.rol == "docente") "Avisos enviados" else "Avisos"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        is Pantalla.FormClase -> Text(if (pantalla.editando == null) "Nueva clase" else "Reagendar clase")
        is Pantalla.Detalle -> Text("Detalle de la clase")
        is Pantalla.NuevoAviso -> Text("Nuevo aviso")
        Pantalla.Acceso -> Unit
    }
}
