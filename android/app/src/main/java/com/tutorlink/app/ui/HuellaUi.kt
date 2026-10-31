package com.tutorlink.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.tutorlink.app.R
import com.tutorlink.app.data.Huella

/** El diálogo de huella del sistema necesita la actividad que muestra la app. */
tailrec fun Context.actividad(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> error("La app debe mostrarse en una FragmentActivity")
}

/** Botón del login para entrar con la huella guardada. */
@Composable
fun BotonHuella(vm: AppViewModel, cuenta: String) {
    val actividad = LocalContext.current.actividad()
    val pulso = rememberInfiniteTransition(label = "pulso")
    val escala by pulso.animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "escala",
    )
    Surface(
        onClick = { ingresarConHuella(vm, actividad) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_fingerprint), null,
                Modifier.size(40.dp).graphicsLayer { scaleX = escala; scaleY = escala },
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Ingresar con huella", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(cuenta, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

fun ingresarConHuella(vm: AppViewModel, actividad: FragmentActivity) {
    vm.limpiarErrorAcceso()
    vm.huella.ingresar(actividad) { resultado ->
        when (resultado) {
            is Huella.Resultado.Ok -> vm.loginConHuella(resultado.email, resultado.password)
            Huella.Resultado.Invalidada -> vm.huellaInvalidada()
            is Huella.Resultado.Error -> vm.errorHuella(resultado.mensaje)
            Huella.Resultado.Cancelada -> Unit
        }
    }
}

/** Después de entrar con contraseña: ¿la próxima vez con huella? */
@Composable
fun DialogoOfertaHuella(vm: AppViewModel) {
    val oferta = vm.ofrecerHuella ?: return
    val actividad = LocalContext.current.actividad()
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = vm::descartarOfertaHuella,
        icon = { Icon(painterResource(R.drawable.ic_fingerprint), null, Modifier.size(40.dp)) },
        title = { Text("¿Entrar con tu huella?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("La próxima vez entrarás a ${oferta.first} solo con tu huella, sin escribir la contraseña.")
                vm.cuentaHuella?.let {
                    Text("Reemplazará la huella configurada para $it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = null
                activar(vm, actividad, oferta.first, oferta.second) { error = it }
            }) { Text("Activar") }
        },
        dismissButton = { TextButton(onClick = vm::descartarOfertaHuella) { Text("Ahora no") } },
    )
}

/** Desde el menú: activar (pidiendo la contraseña) o desactivar el ingreso con huella. */
@Composable
fun DialogoAjustesHuella(vm: AppViewModel, onCerrar: () -> Unit) {
    val actividad = LocalContext.current.actividad()
    val email = vm.usuario?.email?.lowercase().orEmpty()
    val activaAqui = vm.cuentaHuella == email
    var clave by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCerrar,
        icon = { Icon(painterResource(R.drawable.ic_fingerprint), null, Modifier.size(40.dp)) },
        title = { Text("Ingreso con huella") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    activaAqui -> Text("Está activado: puedes entrar a $email con tu huella.")
                    !vm.huella.disponible() -> Text(
                        "Este celular no tiene una huella registrada. Regístrala en los Ajustes del " +
                            "celular (Seguridad → Huella) y vuelve aquí.",
                    )
                    else -> {
                        Text("Confirma tu contraseña para activar la huella en $email.")
                        vm.cuentaHuella?.let {
                            Text("Reemplazará la huella configurada para $it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            clave, { clave = it }, Modifier.fillMaxWidth(), label = { Text("Contraseña") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when {
                activaAqui -> TextButton(onClick = { vm.desactivarHuella(); onCerrar() }) {
                    Text("Desactivar", color = MaterialTheme.colorScheme.error)
                }
                vm.huella.disponible() -> TextButton(
                    enabled = clave.isNotEmpty() && !ocupado,
                    onClick = {
                        ocupado = true
                        error = null
                        vm.verificarClave(
                            clave,
                            alVerificar = { correo, password ->
                                activar(vm, actividad, correo, password) { error = it }
                                ocupado = false
                            },
                            alFallar = { error = it; ocupado = false },
                        )
                    },
                ) { Text(if (ocupado) "Verificando…" else "Activar") }
                else -> TextButton(onClick = onCerrar) { Text("Entendido") }
            }
        },
        dismissButton = { if (activaAqui || vm.huella.disponible()) TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

private fun activar(vm: AppViewModel, actividad: FragmentActivity, email: String, password: String, alError: (String) -> Unit) {
    vm.huella.activar(actividad, email, password) { resultado ->
        when (resultado) {
            is Huella.Resultado.Ok -> vm.huellaActivada(email)
            is Huella.Resultado.Error -> alError(resultado.mensaje)
            Huella.Resultado.Invalidada -> alError("No se pudo activar. Intenta de nuevo.")
            Huella.Resultado.Cancelada -> Unit
        }
    }
}
