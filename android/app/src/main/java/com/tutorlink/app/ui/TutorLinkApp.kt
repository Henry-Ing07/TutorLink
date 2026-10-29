package com.tutorlink.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.tutorlink.app.R

private val colores = lightColorScheme(
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF00897B),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8E7F0),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorLinkApp(vm: AppViewModel) {
    MaterialTheme(colorScheme = colores) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.mensaje) {
            vm.mensaje?.let {
                snackbar.showSnackbar(it)
                vm.mensaje = null
            }
        }
        BackHandler(enabled = vm.puedeVolver) { vm.volver() }

        val pantalla = vm.pantalla
        if (pantalla == Pantalla.Login || pantalla == Pantalla.Registro) {
            Scaffold { padding ->
                androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                    if (pantalla == Pantalla.Login) LoginScreen(vm) else RegistroScreen(vm)
                }
            }
            return@MaterialTheme
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(titulo(pantalla, vm)) },
                    navigationIcon = {
                        if (vm.puedeVolver) {
                            IconButton(onClick = vm::volver) { Icon(painterResource(R.drawable.ic_back), "Volver") }
                        }
                    },
                    actions = {
                        if (pantalla != Pantalla.Avisos) {
                            IconButton(onClick = { vm.ir(Pantalla.Avisos) }) {
                                Icon(painterResource(R.drawable.ic_bell), "Avisos")
                            }
                        }
                        if (!vm.puedeVolver) {
                            IconButton(onClick = vm::salir) { Icon(painterResource(R.drawable.ic_logout), "Cerrar sesión") }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (pantalla == Pantalla.Agenda) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.ir(Pantalla.FormClase(null)) },
                        icon = { Icon(painterResource(R.drawable.ic_add), null) },
                        text = { Text("Nueva clase") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (vm.cargando) LinearProgressIndicator(Modifier.padding(padding))
            Contenido(vm, pantalla, padding)
        }
    }
}

@Composable
private fun Contenido(vm: AppViewModel, pantalla: Pantalla, padding: PaddingValues) {
    when (pantalla) {
        Pantalla.Agenda -> AgendaScreen(vm, padding)
        is Pantalla.FormClase -> FormClaseScreen(vm, pantalla.editando, padding)
        is Pantalla.Detalle -> DetalleClaseScreen(vm, pantalla.clase, padding)
        Pantalla.MisClases -> MisClasesScreen(vm, padding)
        Pantalla.Avisos -> AvisosScreen(vm, padding)
        Pantalla.Login, Pantalla.Registro -> Unit
    }
}

private fun titulo(pantalla: Pantalla, vm: AppViewModel) = when (pantalla) {
    Pantalla.Agenda -> "Mi semana"
    is Pantalla.FormClase -> if (pantalla.editando == null) "Nueva clase" else "Reagendar clase"
    is Pantalla.Detalle -> "Detalle de la clase"
    Pantalla.MisClases -> "Mis clases"
    Pantalla.Avisos -> "Avisos"
    else -> vm.usuario?.nombre.orEmpty()
}
