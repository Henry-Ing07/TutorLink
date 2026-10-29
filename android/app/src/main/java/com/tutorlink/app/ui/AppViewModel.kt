package com.tutorlink.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tutorlink.app.avisos.AvisosWorker
import com.tutorlink.app.data.Api
import com.tutorlink.app.data.Aviso
import com.tutorlink.app.data.Clase
import com.tutorlink.app.data.ErrorApi
import com.tutorlink.app.data.Sesion
import com.tutorlink.app.data.SinConexion
import com.tutorlink.app.data.Usuario
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

sealed interface Pantalla {
    data object Login : Pantalla
    data object Registro : Pantalla
    data object Agenda : Pantalla
    data class FormClase(val editando: Clase?) : Pantalla
    data class Detalle(val clase: Clase) : Pantalla
    data object MisClases : Pantalla
    data object Avisos : Pantalla
}

/** Estado del formulario de clase. Vive en el ViewModel para no perder lo escrito (RNF-02). */
data class FormularioClase(
    val materia: String = "",
    val fecha: LocalDate = LocalDate.now().plusDays(1),
    val hora: LocalTime = LocalTime.of(15, 0),
    val duracionMin: Int = 60,
    val estudiantes: List<Usuario> = emptyList(),
    val repetir: Boolean = false,
    val semanas: Int = 8,
    val error: String? = null,
    val guardando: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val sesion = Sesion(app)
    private val api = Api(sesion)

    var usuario by mutableStateOf(sesion.usuario)
        private set
    private var pila by mutableStateOf(listOf(inicio()))
    val pantalla get() = pila.last()
    val puedeVolver get() = pila.size > 1

    var cargando by mutableStateOf(false)
        private set
    var sinConexion by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
    var errorAcceso by mutableStateOf<String?>(null)
        private set

    var semana by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        private set
    var agenda by mutableStateOf<List<Clase>>(emptyList())
        private set
    var misClases by mutableStateOf<List<Clase>?>(null)
        private set
    var avisos by mutableStateOf<List<Aviso>>(emptyList())
        private set
    var conocidos by mutableStateOf<List<Usuario>>(emptyList())
        private set
    var formulario by mutableStateOf(FormularioClase())
        private set

    init {
        if (usuario != null) entrar()
    }

    private fun inicio(): Pantalla = when (sesion.usuario?.rol) {
        null -> Pantalla.Login
        "docente" -> Pantalla.Agenda
        "estudiante" -> Pantalla.MisClases
        else -> Pantalla.Avisos
    }

    // ---------- Navegación ----------

    fun ir(destino: Pantalla) {
        pila = pila + destino
        sinConexion = false
        when (destino) {
            is Pantalla.FormClase -> abrirFormulario(destino.editando)
            Pantalla.Avisos -> cargarAvisos()
            else -> Unit
        }
    }

    fun volver() {
        if (puedeVolver) pila = pila.dropLast(1)
        recargar()
    }

    private fun reiniciar(destino: Pantalla) {
        pila = listOf(destino)
    }

    // ---------- Sesión ----------

    fun login(email: String, password: String) = accederCon { api.login(email.trim(), password) }

    fun registrar(datos: JSONObject) = accederCon { api.registro(datos) }

    private fun accederCon(accion: suspend () -> Usuario) {
        errorAcceso = null
        viewModelScope.launch {
            cargando = true
            try {
                usuario = accion()
                reiniciar(inicio())
                entrar()
            } catch (e: SinConexion) {
                errorAcceso = "Sin conexión. Revisa tu señal e intenta de nuevo."
            } catch (e: ErrorApi) {
                errorAcceso = e.message
            } finally {
                cargando = false
            }
        }
    }

    private fun entrar() {
        AvisosWorker.programar(getApplication())
        recargar()
    }

    fun salir() {
        AvisosWorker.detener(getApplication())
        sesion.cerrar()
        usuario = null
        agenda = emptyList()
        misClases = null
        avisos = emptyList()
        reiniciar(Pantalla.Login)
    }

    // ---------- Consultas ----------

    fun recargar() {
        when (pantalla) {
            Pantalla.Agenda -> cargarAgenda()
            Pantalla.MisClases -> cargarMisClases()
            Pantalla.Avisos -> cargarAvisos()
            else -> Unit
        }
    }

    fun cambiarSemana(desplazamiento: Long) {
        semana = semana.plusWeeks(desplazamiento)
        cargarAgenda()
    }

    private fun cargarAgenda() {
        val clave = "agenda_$semana"
        consultar(
            alFallarSinRed = { sesion.leerCache(clave)?.let { agenda = it } },
        ) {
            agenda = api.agenda(semana.toString(), semana.plusDays(6).toString())
            sesion.guardarCache(clave, agenda)
        }
    }

    private fun cargarMisClases() = consultar(
        alFallarSinRed = { misClases = sesion.leerCache("mis_clases") ?: misClases },
    ) {
        misClases = api.misClases().also { sesion.guardarCache("mis_clases", it) }
    }

    private fun cargarAvisos() = consultar {
        avisos = api.avisos()
        avisos.filterNot { it.leida }.forEach { runCatching { api.marcarLeido(it.id) } }
    }

    /** Ejecuta una consulta; sin red deja el último dato conocido y activa el aviso. */
    private fun consultar(alFallarSinRed: () -> Unit = {}, bloque: suspend () -> Unit) {
        viewModelScope.launch {
            cargando = true
            try {
                bloque()
                sinConexion = false
            } catch (e: SinConexion) {
                sinConexion = true
                alFallarSinRed()
            } catch (e: ErrorApi) {
                if (e.codigo == 401) salir() else mensaje = e.message
            } finally {
                cargando = false
            }
        }
    }

    // ---------- Formulario de clase (HU-01, HU-02 y reagendar) ----------

    private fun abrirFormulario(clase: Clase?) {
        formulario = if (clase == null) FormularioClase() else FormularioClase(
            materia = clase.materia,
            fecha = clase.inicio.toLocalDate(),
            hora = clase.inicio.toLocalTime(),
            duracionMin = clase.duracionMin,
            estudiantes = clase.estudiantes,
        )
        viewModelScope.launch { runCatching { conocidos = api.misEstudiantes() } }
    }

    fun editar(cambio: FormularioClase.() -> FormularioClase) {
        formulario = formulario.cambio().copy(error = null)
    }

    fun agregarPorCorreo(email: String, listo: () -> Unit) {
        viewModelScope.launch {
            try {
                val estudiante = api.buscarEstudiante(email.trim().lowercase())
                if (formulario.estudiantes.none { it.id == estudiante.id }) {
                    editar { copy(estudiantes = estudiantes + estudiante) }
                }
                if (conocidos.none { it.id == estudiante.id }) conocidos = conocidos + estudiante
                listo()
            } catch (e: SinConexion) {
                formulario = formulario.copy(error = "Sin conexión. Intenta de nuevo.")
            } catch (e: ErrorApi) {
                formulario = formulario.copy(error = e.message)
            }
        }
    }

    fun guardarClase(editando: Clase?, alcance: String = "ocurrencia") {
        val f = formulario
        val inicio = LocalDateTime.of(f.fecha, f.hora)
        val error = when {
            f.materia.isBlank() -> "Escribe la materia"
            f.estudiantes.isEmpty() -> "Agrega al menos un estudiante"
            inicio.isBefore(LocalDateTime.now()) -> "No se pueden crear clases en el pasado"
            else -> null
        }
        if (error != null) {
            formulario = f.copy(error = error)
            return
        }
        val ids = JSONArray(f.estudiantes.map { it.id })
        val datos = JSONObject().put("materia", f.materia.trim()).put("duracion_min", f.duracionMin)
            .put("estudiantes", ids)
        viewModelScope.launch {
            formulario = f.copy(guardando = true)
            try {
                val respuesta = when {
                    editando != null -> api.reagendar(
                        editando.id, datos.put("inicio", inicio.toString()).put("alcance", alcance),
                    )
                    f.repetir -> api.crearSerie(
                        datos.put("dia_semana", f.fecha.dayOfWeek.value - 1)
                            .put("hora_inicio", f.hora.toString())
                            .put("fecha_inicio", f.fecha.toString())
                            .put("fecha_fin", f.fecha.plusWeeks(f.semanas - 1L).toString()),
                    )
                    else -> api.crearClase(datos.put("inicio", inicio.toString()))
                }
                semana = f.fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                mensaje = mensajeGuardado(editando != null, respuesta)
                reiniciar(Pantalla.Agenda)
                cargarAgenda()
            } catch (e: SinConexion) {
                // Flujo E3: no hay cambio parcial y lo escrito se conserva.
                formulario = f.copy(error = "Sin conexión. Tus datos siguen aquí; toca Guardar para reintentar.")
            } catch (e: ErrorApi) {
                val conflicto = e.conflicto
                formulario = f.copy(
                    error = if (conflicto != null) {
                        "${e.message}: ${conflicto.materia}, ${Formato.diaCorto(conflicto.inicio)} " +
                            "${Formato.rango(conflicto.inicio, conflicto.fin)}"
                    } else {
                        e.message
                    },
                )
            }
        }
    }

    fun cancelarClase(clase: Clase, alcance: String) {
        viewModelScope.launch {
            try {
                val respuesta = api.cancelar(clase.id, alcance)
                val n = respuesta.optJSONArray("canceladas")?.length() ?: 1
                mensaje = (if (n > 1) "Se cancelaron $n clases." else "Clase cancelada.") +
                    pendientes(respuesta)
                reiniciar(Pantalla.Agenda)
                cargarAgenda()
            } catch (e: SinConexion) {
                mensaje = "Sin conexión. La clase no se canceló; intenta de nuevo."
            } catch (e: ErrorApi) {
                mensaje = e.message
            }
        }
    }

    private fun mensajeGuardado(edicion: Boolean, respuesta: JSONObject): String {
        val base = when {
            edicion && !respuesta.optBoolean("cambios", true) -> "No hubo cambios que guardar."
            edicion -> "Clase actualizada. Los estudiantes recibirán el aviso."
            respuesta.has("serie_id") ->
                "Se crearon ${respuesta.getJSONArray("clases").length()} clases semanales."
            else -> "Clase creada."
        }
        return base + pendientes(respuesta)
    }

    private fun pendientes(respuesta: JSONObject): String {
        val n = respuesta.optInt("avisos_pendientes")
        return if (n > 0) " $n aviso(s) quedaron pendientes; confírmalo por tu canal habitual." else ""
    }
}
