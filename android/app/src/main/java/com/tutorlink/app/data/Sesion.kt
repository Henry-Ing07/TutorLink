package com.tutorlink.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Token de sesión, usuario actual y la última consulta del horario, que se
 * muestra cuando no hay conexión (RNF-02). Todo en almacenamiento privado de la app.
 */
class Sesion(context: Context) {
    private val prefs = context.getSharedPreferences("tutorlink", Context.MODE_PRIVATE)

    val token: String? get() = prefs.getString("token", null)

    val usuario: Usuario?
        get() = prefs.getString("usuario", null)?.let { Usuario.de(JSONObject(it)) }

    fun iniciar(token: String, usuario: Usuario) {
        val json = JSONObject().put("id", usuario.id).put("nombre", usuario.nombre)
            .put("email", usuario.email).put("rol", usuario.rol)
        prefs.edit {
            clear()
            putString("token", token)
            putString("usuario", json.toString())
        }
    }

    fun cerrar() = prefs.edit { clear() }

    var ultimoAviso: Int
        get() = prefs.getInt("ultimo_aviso", 0)
        set(valor) = prefs.edit { putInt("ultimo_aviso", valor) }

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
        prefs.edit { putString("cache_$clave", lista.toString()) }
    }

    fun leerCache(clave: String): List<Clase>? =
        prefs.getString("cache_$clave", null)?.let { Clase.lista(JSONArray(it)) }
}
