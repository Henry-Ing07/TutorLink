package com.tutorlink.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tutorlink.app.R
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/** Pantalla de acceso: fondo animado, logo y tarjeta que alterna entre ingresar y registrarse. */
@Composable
fun AccesoScreen(vm: AppViewModel) {
    var registro by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        FondoAnimado()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoAnimado(pequeno = registro)
            Spacer(Modifier.height(12.dp))
            Text("TutorLink", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                "Tus asesorías organizadas, sin perderte en el chat",
                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            val entrada = remember { Animatable(0f) }
            LaunchedEffect(Unit) { entrada.animateTo(1f, tween(600, 150, FastOutSlowInEasing)) }
            Card(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = entrada.value
                    translationY = (1f - entrada.value) * 120f
                },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    SelectorModo(registro) {
                        registro = it
                        vm.limpiarErrorAcceso()
                    }
                    Spacer(Modifier.height(20.dp))
                    AnimatedContent(
                        registro,
                        transitionSpec = {
                            val dir = if (targetState) 1 else -1
                            (slideInHorizontally { it * dir / 3 } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally { -it * dir / 3 } + fadeOut(tween(150)))
                        },
                        label = "modo",
                    ) { esRegistro ->
                        if (esRegistro) FormRegistro(vm) else FormLogin(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun FondoAnimado() {
    val t = rememberInfiniteTransition(label = "fondo")
    val fase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "fase",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.linearGradient(
                listOf(Marca.azulOscuro, Marca.azul, Marca.azulClaro),
                start = Offset(size.width * (0.5f + 0.5f * cos(fase)), 0f),
                end = Offset(size.width * (0.5f - 0.5f * cos(fase)), size.height),
            ),
        )
        // Burbujas suaves que se desplazan lentamente.
        listOf(
            Triple(0.15f, 0.12f, 0.42f), Triple(0.85f, 0.30f, 0.30f), Triple(0.30f, 0.75f, 0.36f),
        ).forEachIndexed { i, (x, y, r) ->
            val centro = Offset(
                size.width * x + 40f * cos(fase + i * 2f),
                size.height * y + 60f * sin(fase + i * 1.5f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), centro, size.width * r),
                size.width * r, centro,
            )
        }
    }
}

@Composable
private fun LogoAnimado(pequeno: Boolean) {
    val escala = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { escala.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    val flotar = rememberInfiniteTransition(label = "logo")
    val dy by flotar.animateFloat(
        -5f, 5f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy",
    )
    val tamano by androidx.compose.animation.core.animateDpAsState(if (pequeno) 84.dp else 124.dp, label = "tam")
    Surface(
        Modifier.size(tamano).graphicsLayer {
            scaleX = escala.value
            scaleY = escala.value
            translationY = dy.dp.toPx()
        },
        shape = CircleShape, color = Color.White, shadowElevation = 12.dp,
    ) {
        Image(painterResource(R.drawable.logo_tutorlink), "Logo de TutorLink", Modifier.padding(tamano / 7))
    }
}

@Composable
private fun SelectorModo(registro: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        listOf(false to "Ingresar", true to "Crear cuenta").forEach { (valor, texto) ->
            val activo = registro == valor
            val fondo by animateColorAsState(
                if (activo) MaterialTheme.colorScheme.primary else Color.Transparent, label = "fondo",
            )
            val letra by animateColorAsState(
                if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "letra",
            )
            Surface(
                onClick = { onCambio(valor) }, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50), color = fondo,
            ) {
                Text(
                    texto, Modifier.padding(vertical = 10.dp), color = letra,
                    textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CampoClave(valor: String, onCambio: (String) -> Unit, etiqueta: String, onListo: (() -> Unit)? = null) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        valor, onCambio, Modifier.fillMaxWidth(), label = { Text(etiqueta) }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (onListo != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onListo?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                    if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                )
            }
        },
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun ErrorAcceso(vm: AppViewModel) {
    val desplazamiento = rememberSacudida(vm.errorAcceso)
    AnimatedVisibility(vm.errorAcceso != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(
            Modifier.fillMaxWidth().padding(top = 12.dp).graphicsLayer { translationX = desplazamiento },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                vm.errorAcceso.orEmpty(), Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BotonPrincipal(texto: String, cargando: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = habilitado && !cargando,
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(50),
    ) {
        AnimatedContent(cargando, label = "boton") { ocupado ->
            if (ocupado) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Text(texto, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun FormLogin(vm: AppViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val listo = email.isNotBlank() && password.isNotBlank()
    Column(verticalArrangement = espacio) {
        Text("Bienvenido de nuevo", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.medium,
        )
        CampoClave(password, { password = it }, "Contraseña") { if (listo) vm.login(email, password) }
        ErrorAcceso(vm)
        Spacer(Modifier.height(4.dp))
        BotonPrincipal("Ingresar", vm.cargando, listo) { vm.login(email, password) }
    }
}

@Composable
private fun FormRegistro(vm: AppViewModel) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rol by rememberSaveable { mutableStateOf("docente") }
    var esMenor by rememberSaveable { mutableStateOf(false) }
    var acudienteEmail by rememberSaveable { mutableStateOf("") }
    var autoriza by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = espacio) {
        Text("¿Quién eres?", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OpcionRol(Modifier.weight(1f), R.drawable.ic_school, "Docente", rol == "docente") { rol = "docente" }
            OpcionRol(Modifier.weight(1f), R.drawable.ic_person, "Estudiante", rol == "estudiante") { rol = "estudiante" }
            OpcionRol(Modifier.weight(1f), R.drawable.ic_family, "Acudiente", rol == "acudiente") { rol = "acudiente" }
        }
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(),
            label = { Text("Nombre completo") }, singleLine = true, shape = MaterialTheme.shapes.medium)
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = MaterialTheme.shapes.medium,
        )
        CampoClave(password, { password = it }, "Contraseña (mínimo 8 caracteres)")

        AnimatedVisibility(rol == "estudiante") {
            Column(verticalArrangement = espacio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Soy menor de edad", Modifier.weight(1f))
                    Switch(esMenor, { esMenor = it })
                }
                AnimatedVisibility(esMenor) {
                    OutlinedTextField(
                        acudienteEmail, { acudienteEmail = it }, Modifier.fillMaxWidth(),
                        label = { Text("Correo de tu acudiente") }, singleLine = true,
                        supportingText = { Text("Tu acudiente crea su cuenta primero y recibe tus avisos.") },
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        }
        AnimatedVisibility(rol == "acudiente") {
            Text(
                "Verás las clases del menor a tu cargo, recibirás sus avisos y podrás escribir a sus docentes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(autoriza, { autoriza = it })
            Text(
                if (rol == "acudiente") {
                    "Autorizo el tratamiento de mis datos y los del menor a mi cargo (Ley 1581 de 2012)"
                } else {
                    "Autorizo el tratamiento de mis datos personales (Ley 1581 de 2012)"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ErrorAcceso(vm)
        BotonPrincipal(
            "Crear cuenta", vm.cargando,
            autoriza && nombre.isNotBlank() && email.isNotBlank() && password.length >= 8,
        ) {
            val datos = JSONObject().put("nombre", nombre.trim()).put("email", email.trim())
                .put("password", password).put("rol", rol).put("autoriza_datos", autoriza)
            if (rol == "estudiante" && esMenor) {
                datos.put("es_menor", true).put("acudiente_email", acudienteEmail.trim())
            }
            vm.registrar(datos)
        }
    }
}

@Composable
private fun OpcionRol(modifier: Modifier, @DrawableRes icono: Int, texto: String, activo: Boolean, onClick: () -> Unit) {
    val borde by animateColorAsState(
        if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "borde",
    )
    val fondo by animateColorAsState(
        if (activo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, label = "fondo",
    )
    val escala by androidx.compose.animation.core.animateFloatAsState(if (activo) 1.04f else 1f, label = "escala")
    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = escala; scaleY = escala },
        shape = MaterialTheme.shapes.medium, color = fondo, border = BorderStroke(if (activo) 2.dp else 1.dp, borde),
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icono), null, tint = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(texto, style = MaterialTheme.typography.labelMedium)
        }
    }
}
