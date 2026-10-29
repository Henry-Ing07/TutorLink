package com.tutorlink.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
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
    /** Solo en la vista del acudiente: el menor inscrito en la clase. */
    val estudiante: String? = null,
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
                estudiante = json.optString("estudiante").takeIf { it.isNotEmpty() },
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
    val remitenteId: Int?,
    val creadaEn: Instant,
) {
    companion object {
        fun de(json: JSONObject) = Aviso(
            id = json.getInt("id"),
            tipo = json.getString("tipo"),
            titulo = json.getString("titulo"),
            mensaje = json.getString("mensaje"),
            leida = json.optBoolean("leida"),
            remitenteId = if (json.isNull("remitente_id")) null else json.optInt("remitente_id"),
            creadaEn = Instant.parse(json.getString("creada_en")),
        )

        fun lista(json: JSONArray) = json.objetos().map(::de)
    }
}

/** Aviso que el docente escribió y envió. */
data class Anuncio(
    val id: Int,
    val titulo: String,
    val mensaje: String,
    val materia: String?,
    val destinatarios: Int,
    val creadoEn: Instant,
) {
    companion object {
        fun de(json: JSONObject) = Anuncio(
            id = json.getInt("id"),
            titulo = json.getString("titulo"),
            mensaje = json.getString("mensaje"),
            materia = json.optJSONObject("clase")?.optString("materia"),
            destinatarios = json.optInt("destinatarios"),
            creadoEn = Instant.parse(json.getString("creado_en")),
        )

        fun lista(json: JSONArray) = json.objetos().map(::de)
    }
}

data class MensajeChat(val id: Int, val texto: String, val mio: Boolean, val creadoEn: Instant) {
    companion object {
        fun de(json: JSONObject) = MensajeChat(
            id = json.getInt("id"),
            texto = json.getString("texto"),
            mio = json.getBoolean("mio"),
            creadoEn = Instant.parse(json.getString("creado_en")),
        )

        fun lista(json: JSONArray) = json.objetos().map(::de)
    }
}

/** Persona con la que el usuario puede conversar, con el resumen de la conversación. */
data class Contacto(
    val id: Int,
    val nombre: String,
    val rol: String,
    val relacion: String,
    val ultimo: MensajeChat?,
    val noLeidos: Int,
) {
    companion object {
        fun de(json: JSONObject): Contacto {
            val usuario = json.getJSONObject("usuario")
            return Contacto(
                id = usuario.getInt("id"),
                nombre = usuario.getString("nombre"),
                rol = usuario.optString("rol"),
                relacion = json.optString("relacion"),
                ultimo = json.optJSONObject("ultimo")?.let(MensajeChat::de),
                noLeidos = json.optInt("no_leidos"),
            )
        }

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
