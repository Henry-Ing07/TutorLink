package com.tutorlink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun LoginScreen(vm: AppViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TutorLink", style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary)
        Text("Tus asesorías, sin perderte en el chat.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Contraseña") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        vm.errorAcceso?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.login(email, password) },
            enabled = !vm.cargando && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.cargando) "Ingresando…" else "Ingresar") }
        TextButton(onClick = { vm.ir(Pantalla.Registro) }, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Crear una cuenta")
        }
    }
}

@Composable
fun RegistroScreen(vm: AppViewModel) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rol by rememberSaveable { mutableStateOf("docente") }
    var esMenor by rememberSaveable { mutableStateOf(false) }
    var acudienteEmail by rememberSaveable { mutableStateOf("") }
    var autoriza by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = espacio,
    ) {
        Text("Crear cuenta", style = MaterialTheme.typography.headlineSmall)
        Text("Soy:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("docente" to "Docente", "estudiante" to "Estudiante", "acudiente" to "Acudiente")
                .forEach { (valor, etiqueta) ->
                    FilterChip(rol == valor, { rol = valor }, label = { Text(etiqueta) })
                }
        }
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(),
            label = { Text("Nombre") }, singleLine = true)
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(),
            label = { Text("Contraseña (mínimo 8 caracteres)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (rol == "estudiante") {
            Fila(esMenor, { esMenor = it }, "Soy menor de edad")
            if (esMenor) {
                OutlinedTextField(
                    acudienteEmail, { acudienteEmail = it }, Modifier.fillMaxWidth(),
                    label = { Text("Correo de tu acudiente") }, singleLine = true,
                    supportingText = { Text("Tu acudiente debe crear su cuenta primero y recibirá tus avisos.") },
                )
            }
        }
        if (rol == "acudiente") {
            Text(
                "Como acudiente recibirás los avisos de las clases del menor a tu cargo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Fila(
            autoriza, { autoriza = it },
            if (rol == "acudiente") {
                "Autorizo el tratamiento de mis datos y los del menor a mi cargo (Ley 1581 de 2012)"
            } else {
                "Autorizo el tratamiento de mis datos personales (Ley 1581 de 2012)"
            },
        )
        vm.errorAcceso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val datos = JSONObject().put("nombre", nombre.trim()).put("email", email.trim())
                    .put("password", password).put("rol", rol).put("autoriza_datos", autoriza)
                if (rol == "estudiante" && esMenor) {
                    datos.put("es_menor", true).put("acudiente_email", acudienteEmail.trim())
                }
                vm.registrar(datos)
            },
            enabled = !vm.cargando && autoriza && nombre.isNotBlank() && email.isNotBlank() &&
                password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crear cuenta") }
        TextButton(onClick = vm::volver, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Ya tengo cuenta")
        }
    }
}

@Composable
private fun Fila(marcado: Boolean, onCambio: (Boolean) -> Unit, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(marcado, onCambio)
        Text(texto, Modifier.weight(1f))
    }
}
