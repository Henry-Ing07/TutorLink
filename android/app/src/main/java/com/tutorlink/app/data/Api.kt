package com.tutorlink.app.data

import android.util.Log
import com.tutorlink.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Error que el backend devolvió con un código HTTP y un mensaje para el usuario. */
class ErrorApi(
    val codigo: Int,
    override val message: String,
    val cuerpo: JSONObject?,
) : Exception(message) {
    val conflicto get() = Conflicto.de(cuerpo?.optJSONObject("conflicto"))
}

/** Sin red o el servidor no respondió a tiempo (RNF-02). */
class SinConexion(causa: Throwable) : IOException(causa)

/**
 * Cliente HTTP mínimo sobre HttpURLConnection y org.json, ambos incluidos en
 * Android: sin dependencias externas que auditar (ADR-0005).
 */
class Api(private val sesion: Sesion, private val baseUrl: String = BuildConfig.API_URL) {

    suspend fun login(email: String, password: String) =
        pedir("POST", "/auth/login", JSONObject().put("email", email).put("password", password))
            .let(::guardarSesion)

    suspend fun registro(datos: JSONObject) = pedir("POST", "/auth/registro", datos)
        .let(::guardarSesion)

    suspend fun agenda(desde: String, hasta: String): List<Clase> {
        val json = pedir("GET", "/docentes/me/clases?desde=$desde&hasta=$hasta")
        return Clase.lista(json.getJSONArray("clases"))
    }

    suspend fun misClases(): List<Clase> = Clase.lista(pedirLista("/estudiantes/me/clases"))

    suspend fun misEstudiantes(): List<Usuario> =
        pedirLista("/docentes/me/estudiantes").objetos().map(Usuario::de)

    suspend fun buscarEstudiante(email: String) =
        Usuario.de(pedir("GET", "/estudiantes/buscar?email=${java.net.URLEncoder.encode(email, "UTF-8")}"))

    suspend fun crearClase(datos: JSONObject) = pedir("POST", "/clases", datos)

    suspend fun crearSerie(datos: JSONObject) = pedir("POST", "/series", datos)

    suspend fun reagendar(id: Int, datos: JSONObject) = pedir("PUT", "/clases/$id", datos)

    suspend fun cancelar(id: Int, alcance: String) =
        pedir("POST", "/clases/$id/cancelar", JSONObject().put("alcance", alcance))

    suspend fun avisos(despuesDe: Int = 0, incluirMensajes: Boolean = false): List<Aviso> {
        val extra = if (incluirMensajes) "&incluir_mensajes=1" else ""
        return Aviso.lista(pedirLista("/notificaciones/me?despues_de=$despuesDe$extra"))
    }

    suspend fun marcarLeido(id: Int) {
        pedir("POST", "/notificaciones/$id/leida")
    }

    suspend fun clasesDeMisMenores(): List<Clase> = Clase.lista(pedirLista("/acudientes/me/clases"))

    // ---------- Avisos del docente ----------

    suspend fun enviarAnuncio(datos: JSONObject) = pedir("POST", "/anuncios", datos)

    suspend fun anunciosEnviados(): List<Anuncio> = Anuncio.lista(pedirLista("/anuncios/enviados"))

    // ---------- Mensajes privados ----------

    suspend fun contactos(): List<Contacto> = Contacto.lista(pedirLista("/contactos"))

    suspend fun mensajesNoLeidos(): Int = pedir("GET", "/mensajes/no-leidos").optInt("no_leidos")

    suspend fun conversacion(con: Int, despuesDe: Int = 0): List<MensajeChat> =
        MensajeChat.lista(pedirLista("/mensajes/$con?despues_de=$despuesDe"))

    suspend fun enviarMensaje(a: Int, texto: String) =
        MensajeChat.de(pedir("POST", "/mensajes/$a", JSONObject().put("texto", texto)))

    private fun guardarSesion(json: JSONObject): Usuario {
        val usuario = Usuario.de(json.getJSONObject("usuario"))
        sesion.iniciar(json.getString("token"), usuario)
        return usuario
    }

    private suspend fun pedirLista(ruta: String): JSONArray = JSONArray(enviar("GET", ruta, null))

    private suspend fun pedir(metodo: String, ruta: String, cuerpo: JSONObject? = null): JSONObject {
        val texto = enviar(metodo, ruta, cuerpo)
        return if (texto.isBlank()) JSONObject() else JSONObject(texto)
    }

    private suspend fun enviar(metodo: String, ruta: String, cuerpo: JSONObject?): String =
        withContext(Dispatchers.IO) {
            val conexion = URL(baseUrl + ruta).openConnection() as HttpURLConnection
            try {
                conexion.requestMethod = metodo
                // Fallar rápido sin señal: el indicador debe aparecer en menos de 3 s (RNF-02).
                conexion.connectTimeout = 2_500
                conexion.readTimeout = 10_000
                conexion.setRequestProperty("Accept", "application/json")
                sesion.token?.let { conexion.setRequestProperty("Authorization", "Bearer $it") }
                if (cuerpo != null) {
                    conexion.doOutput = true
                    conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conexion.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
                }
                val codigo = conexion.responseCode
                val flujo = if (codigo < 400) conexion.inputStream else conexion.errorStream
                val texto = flujo?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (codigo >= 400) throw errorDe(codigo, texto)
                texto
            } catch (e: IOException) {
                // Sin la ruta: puede llevar el correo de un estudiante (DoD punto 8).
                Log.w("TutorLink", "Fallo de red en $metodo: ${e.javaClass.simpleName} ${e.message}")
                throw SinConexion(e)
            } finally {
                conexion.disconnect()
            }
        }

    private fun errorDe(codigo: Int, texto: String): ErrorApi {
        val json = runCatching { JSONObject(texto) }.getOrNull()
        if (codigo == 401) sesion.cerrar()
        val mensaje = json?.optString("error")?.takeIf { it.isNotBlank() } ?: when (codigo) {
            403 -> "No tienes acceso a esta información"
            else -> "Ocurrió un error ($codigo). Intenta de nuevo."
        }
        return ErrorApi(codigo, mensaje, json)
    }
}
