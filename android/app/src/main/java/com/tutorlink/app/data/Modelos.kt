package com.tutorlink.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

data class Usuario(
    val id: Int,
    val nombre: String,
    val email: String,
    val rol: String,
) {
    companion object {
        fun de(json: JSONObject) = Usuario(
            id = json.getInt("id"),
            nombre = json.getString("nombre"),
            email = json.optString("email"),
            rol = json.optString("rol"),
        )
    }
}

data class Clase(
    val id: Int,
    val materia: String,
    val inicio: LocalDateTime,
    val fin: LocalDateTime,
    val duracionMin: Int,
    val serieId: Int?,
    val docente: String,
    val estudiantes: List<Usuario>,
) {
    val esRecurrente get() = serieId != null

    companion object {
        fun de(json: JSONObject): Clase {
            val docente = json.optJSONObject("docente")
            return Clase(
                id = json.getInt("id"),
                materia = json.getString("materia"),
                inicio = LocalDateTime.parse(json.getString("inicio")),
                fin = LocalDateTime.parse(json.getString("fin")),
                duracionMin = json.getInt("duracion_min"),
                serieId = if (json.isNull("serie_id")) null else json.optInt("serie_id"),
                docente = docente?.optString("nombre").orEmpty(),
                estudiantes = json.optJSONArray("estudiantes").objetos().map(Usuario::de),
            )
        }

        fun lista(json: JSONArray) = json.objetos().map(::de)
    }
}

data class Aviso(
    val id: Int,
    val tipo: String,
    val titulo: String,
    val mensaje: String,
    val leida: Boolean,
) {
    companion object {
        fun de(json: JSONObject) = Aviso(
            id = json.getInt("id"),
            tipo = json.getString("tipo"),
            titulo = json.getString("titulo"),
            mensaje = json.getString("mensaje"),
            leida = json.optBoolean("leida"),
        )

        fun lista(json: JSONArray) = json.objetos().map(::de)
    }
}

/** Clase con la que choca un horario propuesto (respuesta 409 del backend). */
data class Conflicto(val materia: String, val inicio: LocalDateTime, val fin: LocalDateTime) {
    companion object {
        fun de(json: JSONObject?) = json?.let {
            Conflicto(
                it.getString("materia"),
                LocalDateTime.parse(it.getString("inicio")),
                LocalDateTime.parse(it.getString("fin")),
            )
        }
    }
}

fun JSONArray?.objetos(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }
