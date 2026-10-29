"""Modelo de datos de TutorLink.

Decisión de recurrencia (ADR-0002): una franja semanal (Serie) genera clases
individuales (Clase) al crearse. Cada ocurrencia se puede reagendar o cancelar
sin tocar las demás, y la validación de cruces trabaja siempre sobre clases
concretas.
"""
from datetime import datetime, timedelta

from .extensions import db

ROLES = ("docente", "estudiante", "acudiente")

clase_estudiante = db.Table(
    "clase_estudiante",
    db.Column("clase_id", db.Integer, db.ForeignKey("clases.id", ondelete="CASCADE"),
              primary_key=True),
    db.Column("estudiante_id", db.Integer, db.ForeignKey("usuarios.id", ondelete="CASCADE"),
              primary_key=True),
)


class Usuario(db.Model):
    __tablename__ = "usuarios"

    id = db.Column(db.Integer, primary_key=True)
    nombre = db.Column(db.String(80), nullable=False)
    email = db.Column(db.String(120), nullable=False, unique=True, index=True)
    password_hash = db.Column(db.String(60), nullable=False)
    rol = db.Column(db.String(12), nullable=False)
    # Ley 1581 de 2012: del menor solo se guarda nombre, correo y el vínculo
    # con su acudiente, que es quien autoriza el tratamiento de los datos.
    es_menor = db.Column(db.Boolean, nullable=False, default=False)
    acudiente_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=True)
    autoriza_datos_en = db.Column(db.DateTime, nullable=False)
    creado_en = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    acudiente = db.relationship("Usuario", remote_side=[id])

    def resumen(self):
        return {"id": self.id, "nombre": self.nombre, "email": self.email}

    def perfil(self):
        datos = self.resumen()
        datos["rol"] = self.rol
        if self.rol == "estudiante":
            datos["es_menor"] = self.es_menor
        return datos


class Serie(db.Model):
    __tablename__ = "series"

    id = db.Column(db.Integer, primary_key=True)
    docente_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=False)
    materia = db.Column(db.String(80), nullable=False)
    dia_semana = db.Column(db.Integer, nullable=False)
    hora_inicio = db.Column(db.Time, nullable=False)
    duracion_min = db.Column(db.Integer, nullable=False)
    fecha_inicio = db.Column(db.Date, nullable=False)
    fecha_fin = db.Column(db.Date, nullable=False)

    __table_args__ = (
        db.CheckConstraint("dia_semana BETWEEN 0 AND 6", name="ck_serie_dia"),
        db.CheckConstraint("duracion_min BETWEEN 30 AND 240", name="ck_serie_duracion"),
    )


class Clase(db.Model):
    __tablename__ = "clases"

    id = db.Column(db.Integer, primary_key=True)
    docente_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=False)
    serie_id = db.Column(db.Integer, db.ForeignKey("series.id"), nullable=True)
    materia = db.Column(db.String(80), nullable=False)
    inicio = db.Column(db.DateTime, nullable=False)
    # El fin se guarda explícito para poder indexar la búsqueda de cruces (RNF-01).
    fin = db.Column(db.DateTime, nullable=False)
    duracion_min = db.Column(db.Integer, nullable=False)
    estado = db.Column(db.String(10), nullable=False, default="activa")
    creada_en = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    docente = db.relationship("Usuario", foreign_keys=[docente_id])
    serie = db.relationship("Serie")
    estudiantes = db.relationship("Usuario", secondary=clase_estudiante, lazy="selectin",
                                  order_by="Usuario.nombre")

    __table_args__ = (
        db.CheckConstraint("duracion_min BETWEEN 30 AND 240", name="ck_clase_duracion"),
        db.CheckConstraint("estado IN ('activa', 'cancelada')", name="ck_clase_estado"),
        db.Index("ix_clases_docente_inicio", "docente_id", "inicio"),
    )

    def programar(self, inicio, duracion_min):
        self.inicio = inicio
        self.duracion_min = duracion_min
        self.fin = inicio + timedelta(minutes=duracion_min)

    def resumen(self):
        return {
            "id": self.id,
            "materia": self.materia,
            "inicio": self.inicio.isoformat(timespec="minutes"),
            "fin": self.fin.isoformat(timespec="minutes"),
            "duracion_min": self.duracion_min,
        }

    def a_dict(self, con_estudiantes=True):
        datos = self.resumen()
        datos.update({
            "estado": self.estado,
            "serie_id": self.serie_id,
            "docente": {"id": self.docente.id, "nombre": self.docente.nombre},
        })
        if con_estudiantes:
            datos["estudiantes"] = [e.resumen() for e in self.estudiantes]
        return datos


class Notificacion(db.Model):
    __tablename__ = "notificaciones"

    id = db.Column(db.Integer, primary_key=True)
    destinatario_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=False)
    # Si el aviso va al acudiente, aquí queda el estudiante al que se refiere.
    estudiante_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=True)
    # Los avisos de clase llevan clase_id; los anuncios y mensajes pueden no tenerla.
    clase_id = db.Column(db.Integer, db.ForeignKey("clases.id"), nullable=True)
    # Quien originó el aviso cuando es una persona (anuncio o mensaje del docente, etc.).
    remitente_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=True)
    tipo = db.Column(db.String(12), nullable=False)
    titulo = db.Column(db.String(120), nullable=False)
    mensaje = db.Column(db.String(400), nullable=False)
    estado = db.Column(db.String(10), nullable=False, default="pendiente")
    intentos = db.Column(db.Integer, nullable=False, default=0)
    respuesta = db.Column(db.String(200), nullable=True)
    leida = db.Column(db.Boolean, nullable=False, default=False)
    creada_en = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    destinatario = db.relationship("Usuario", foreign_keys=[destinatario_id])

    __table_args__ = (
        db.Index("ix_notificaciones_destinatario", "destinatario_id", "creada_en"),
    )

    def a_dict(self):
        return {
            "id": self.id,
            "clase_id": self.clase_id,
            "remitente_id": self.remitente_id,
            "tipo": self.tipo,
            "titulo": self.titulo,
            "mensaje": self.mensaje,
            "leida": self.leida,
            "creada_en": self.creada_en.isoformat(timespec="seconds") + "Z",
        }


class Anuncio(db.Model):
    """Aviso que el docente escribe a sus estudiantes (y acudientes de los menores)."""
    __tablename__ = "anuncios"

    id = db.Column(db.Integer, primary_key=True)
    docente_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=False)
    clase_id = db.Column(db.Integer, db.ForeignKey("clases.id"), nullable=True)
    titulo = db.Column(db.String(80), nullable=False)
    mensaje = db.Column(db.String(400), nullable=False)
    destinatarios = db.Column(db.Integer, nullable=False, default=0)
    creado_en = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    clase = db.relationship("Clase")

    def a_dict(self):
        return {
            "id": self.id,
            "titulo": self.titulo,
            "mensaje": self.mensaje,
            "clase": self.clase.resumen() if self.clase else None,
            "destinatarios": self.destinatarios,
            "creado_en": self.creado_en.isoformat(timespec="seconds") + "Z",
        }


class Mensaje(db.Model):
    """Mensaje privado entre un docente y un estudiante mayor de edad o un acudiente."""
    __tablename__ = "mensajes"

    id = db.Column(db.Integer, primary_key=True)
    remitente_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=False)
    destinatario_id = db.Column(db.Integer, db.ForeignKey("usuarios.id"), nullable=False)
    texto = db.Column(db.String(1000), nullable=False)
    leido = db.Column(db.Boolean, nullable=False, default=False)
    creado_en = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    __table_args__ = (
        db.Index("ix_mensajes_par", "remitente_id", "destinatario_id", "id"),
    )

    def a_dict(self, yo_id):
        return {
            "id": self.id,
            "texto": self.texto,
            "mio": self.remitente_id == yo_id,
            "leido": self.leido,
            "creado_en": self.creado_en.isoformat(timespec="seconds") + "Z",
        }


class BitacoraSeguridad(db.Model):
    __tablename__ = "bitacora_seguridad"

    id = db.Column(db.Integer, primary_key=True)
    usuario_id = db.Column(db.Integer, nullable=True)
    evento = db.Column(db.String(40), nullable=False)
    recurso = db.Column(db.String(120), nullable=False)
    creado_en = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)
