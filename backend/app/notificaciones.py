"""Generación y despacho de avisos (RF-05, HU-04).

El cambio de una clase y el envío de su aviso no son atómicos (flujo E2 del
caso de uso «Reagendar una clase»): la clase se guarda primero y los avisos
que el proveedor no confirma quedan pendientes para reintento.
"""
import logging

from flask import current_app

from .extensions import db
from .models import Notificacion

log = logging.getLogger(__name__)

DIAS = ("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
MESES = ("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")


def describir_horario(inicio, fin):
    return (f"{DIAS[inicio.weekday()]} {inicio.day} {MESES[inicio.month - 1]}, "
            f"{inicio:%H:%M} a {fin:%H:%M}")


class NotificadorBandeja:
    """Deja el aviso en la bandeja del destinatario; la app lo descarga y lo
    muestra como notificación del sistema (ADR-0004)."""

    def enviar(self, notificacion):
        return "bandeja: disponible"


class ErrorEntrega(Exception):
    pass


_PROVEEDORES = {"bandeja": NotificadorBandeja}


def proveedor():
    if "notificador" in current_app.extensions:
        return current_app.extensions["notificador"]
    nombre = current_app.config["NOTIFICADOR"]
    if nombre not in _PROVEEDORES:
        raise RuntimeError(f"Proveedor de notificaciones desconocido: {nombre}")
    current_app.extensions["notificador"] = _PROVEEDORES[nombre]()
    return current_app.extensions["notificador"]


def _contenido(clase, tipo, anterior):
    horario = describir_horario(clase.inicio, clase.fin)
    if tipo == "creada":
        return (f"Nueva clase de {clase.materia}",
                f"{horario} con {clase.docente.nombre}.")
    if tipo == "cambio":
        antes = describir_horario(*anterior)
        return (f"{clase.materia} cambió de horario",
                f"Antes: {antes}. Ahora: {horario}.")
    if tipo == "cancelada":
        return (f"{clase.materia} fue cancelada",
                f"La clase del {horario} con {clase.docente.nombre} no se realizará.")
    if tipo == "retirado":
        return (f"Ya no estás en {clase.materia}",
                f"{clase.docente.nombre} te retiró de la clase del {horario}.")
    raise ValueError(tipo)


def avisar(clase, tipo, estudiantes, anterior=None, sufijo=""):
    """Crea un aviso por estudiante y otro para su acudiente si es menor.

    `anterior` es (inicio, fin) previo, solo para el tipo "cambio".
    Devuelve las notificaciones creadas (aún sin despachar).
    """
    titulo, mensaje = _contenido(clase, tipo, anterior)
    return _a_estudiantes(estudiantes, tipo, titulo, mensaje + sufijo, clase_id=clase.id)


def avisar_anuncio(anuncio, docente, estudiantes):
    """Aviso escrito por el docente; también llega al acudiente de cada menor."""
    titulo = f"{docente.nombre}: {anuncio.titulo}"[:120]
    return _a_estudiantes(estudiantes, "anuncio", titulo, anuncio.mensaje,
                          clase_id=anuncio.clase_id, remitente_id=docente.id)


def avisar_mensaje(mensaje, remitente):
    """Notifica un mensaje privado nuevo a su destinatario."""
    texto = mensaje.texto if len(mensaje.texto) <= 200 else mensaje.texto[:197] + "..."
    aviso = Notificacion(destinatario_id=mensaje.destinatario_id, remitente_id=remitente.id,
                         tipo="mensaje", titulo=f"Mensaje de {remitente.nombre}"[:120],
                         mensaje=texto)
    db.session.add(aviso)
    return [aviso]


def _a_estudiantes(estudiantes, tipo, titulo, mensaje, **campos):
    """Un aviso por estudiante y otro para su acudiente si es menor (sin despachar)."""
    creadas = []
    for estudiante in estudiantes:
        creadas.append(Notificacion(destinatario_id=estudiante.id, tipo=tipo, titulo=titulo,
                                    mensaje=mensaje, **campos))
        if estudiante.es_menor and estudiante.acudiente_id:
            creadas.append(Notificacion(
                destinatario_id=estudiante.acudiente_id, estudiante_id=estudiante.id,
                tipo=tipo, titulo=titulo,
                mensaje=f"Aviso para {estudiante.nombre}: {mensaje}"[:400], **campos))
    db.session.add_all(creadas)
    return creadas


def despachar(notificaciones):
    """Intenta entregar cada aviso. Devuelve los que quedaron pendientes."""
    db.session.flush()
    pendientes = []
    maximo = current_app.config["NOTIFICACION_MAX_INTENTOS"]
    for notificacion in notificaciones:
        notificacion.intentos += 1
        try:
            notificacion.respuesta = str(proveedor().enviar(notificacion))[:200]
            notificacion.estado = "enviada"
        except Exception as error:  # el proveedor externo puede fallar de cualquier forma
            notificacion.respuesta = f"error: {error}"[:200]
            notificacion.estado = "fallida" if notificacion.intentos >= maximo else "pendiente"
            # Solo el id: los datos del estudiante no van a los logs (DoD punto 8).
            log.warning("Aviso %s no entregado (intento %s)", notificacion.id,
                        notificacion.intentos)
            pendientes.append(notificacion)
    db.session.commit()
    return pendientes


def reintentar_pendientes():
    pendientes = Notificacion.query.filter_by(estado="pendiente").all()
    return len(pendientes), len(despachar(pendientes))
