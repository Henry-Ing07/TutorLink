package com.tutorlink.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sesión de la app (ADR-0008).
 *
 * El token de sesión y el usuario viven SOLO en memoria: al cerrar la app (salir con Atrás o
 * quitarla de recientes) desaparecen y hay que volver a entrar. En el almacenamiento privado
 * quedan únicamente el token que sirve solo para descargar avisos (lo usa el celular en segundo
 * plano) y la última consulta del horario, que se muestra sin conexión durante la sesión (RNF-02).
 */
class Sesion(context: Context) {
    private val prefs = context.getSharedPreferences("tutorlink", Context.MODE_PRIVATE)

    var token: String? = null
        private set
    var usuario: Usuario? = null
        private set

    /** Token de alcance "avisos": no permite ver clases, mensajes ni cambiar nada. */
    val tokenAvisos: String? get() = prefs.getString(TOKEN_AVISOS, null)

    fun iniciar(token: String, tokenAvisos: String?, usuario: Usuario) {
        val otraCuenta = prefs.getInt(USUARIO_AVISOS, -1) != usuario.id
        this.token = token
        this.usuario = usuario
        prefs.edit {
            borrarCaches()
            // Al cambiar de cuenta se reinicia la bandeja para no mostrar avisos ajenos.
            if (otraCuenta) remove(ULTIMO_AVISO)
            putInt(USUARIO_AVISOS, usuario.id)
            if (tokenAvisos != null) putString(TOKEN_AVISOS, tokenAvisos) else remove(TOKEN_AVISOS)
        }
    }

    /** Cierre de sesión explícito: también deja de recibir avisos en este celular. */
    fun cerrar() {
        token = null
        usuario = null
        prefs.edit { clear() }
    }

    /**
     * Se llama al abrir la app: borra lo que quedó de la sesión anterior (horarios guardados) y
     * lo que guardaban versiones anteriores de la app (token de sesión y usuario en disco).
     */
    fun limpiarAlAbrir() = prefs.edit {
        borrarCaches()
        remove("token")
        remove("usuario")
    }

    /** El servidor rechazó el token de avisos (venció): se deja de consultar hasta el próximo ingreso. */
    fun olvidarTokenAvisos() = prefs.edit { remove(TOKEN_AVISOS) }

    var ultimoAviso: Int
        get() = prefs.getInt(ULTIMO_AVISO, 0)
        set(valor) = prefs.edit { putInt(ULTIMO_AVISO, valor) }

    fun guardarCache(clave: String, clases: List<Clase>) {
        val lista = JSONArray()
        clases.forEach { c ->
            lista.put(
                JSONObject().put("id", c.id).put("materia", c.materia)
                    .put("inicio", c.inicio.toString()).put("fin", c.fin.toString())
                    .put("duracion_min", c.duracionMin).put("serie_id", c.serieId ?: JSONObject.NULL)
                    .put("docente", JSONObject().put("nombre", c.docente))
                    .put("estudiantes", JSONArray(c.estudiantes.map {
                        JSONObject().put("id", it.id).put("nombre", it.nombre).put("email", it.email)
                    })),
            )
        }
        prefs.edit { putString(CACHE + clave, lista.toString()) }
    }

    fun leerCache(clave: String): List<Clase>? =
        prefs.getString(CACHE + clave, null)?.let { Clase.lista(JSONArray(it)) }

    private fun android.content.SharedPreferences.Editor.borrarCaches() {
        prefs.all.keys.filter { it.startsWith(CACHE) }.forEach { remove(it) }
    }

    private companion object {
        const val TOKEN_AVISOS = "token_avisos"
        const val USUARIO_AVISOS = "usuario_avisos"
        const val ULTIMO_AVISO = "ultimo_aviso"
        const val CACHE = "cache_"
    }
}
