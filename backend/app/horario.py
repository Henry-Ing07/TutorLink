"""Caso de uso incluido «Validar disponibilidad de horario» (FPI-06).

Es el único lugar donde se decide si dos clases se cruzan; lo usan crear clase,
crear franja recurrente y reagendar. La comparación es estricta: dos clases que
se tocan en el borde (una termina 15:00, otra empieza 15:00) no se solapan.
"""
from datetime import date, datetime, time, timedelta

from .extensions import db
from .models import Clase
from .seguridad import ErrorApi, ahora

DURACION_MIN = 30
DURACION_MAX = 240
MAX_SEMANAS_SERIE = 52


def leer_materia(valor):
    materia = (valor or "").strip() if isinstance(valor, str) else ""
    if not materia:
        raise ErrorApi(400, "La materia es obligatoria")
    if len(materia) > 80:
        raise ErrorApi(400, "La materia no puede superar 80 caracteres")
    return materia


def leer_duracion(valor):
    if isinstance(valor, bool) or not isinstance(valor, int):
        raise ErrorApi(400, "La duración debe ser un número entero de minutos")
    if not DURACION_MIN <= valor <= DURACION_MAX:
        raise ErrorApi(400, f"La duración debe estar entre {DURACION_MIN} y {DURACION_MAX} minutos")
    return valor


def leer_fecha_hora(valor):
    try:
        momento = datetime.fromisoformat(valor)
    except (TypeError, ValueError):
        raise ErrorApi(400, "La fecha y hora deben tener el formato AAAA-MM-DDTHH:MM")
    if momento.tzinfo is not None:
        raise ErrorApi(400, "La fecha y hora deben enviarse en hora local, sin zona horaria")
    return momento.replace(second=0, microsecond=0)


def leer_fecha(valor, campo):
    try:
        return date.fromisoformat(valor)
    except (TypeError, ValueError):
        raise ErrorApi(400, f"El campo {campo} debe tener el formato AAAA-MM-DD")


def leer_hora(valor):
    try:
        return time.fromisoformat(valor).replace(second=0, microsecond=0)
    except (TypeError, ValueError):
        raise ErrorApi(400, "La hora debe tener el formato HH:MM")


def exigir_futuro(inicio):
    if inicio < ahora():
        raise ErrorApi(400, "No se pueden crear clases en el pasado")


def ocurrencias_semanales(dia_semana, hora, fecha_inicio, fecha_fin):
    """Fechas de inicio de cada jueves/lunes/... entre las dos fechas, inclusive."""
    if not isinstance(dia_semana, int) or isinstance(dia_semana, bool) \
            or not 0 <= dia_semana <= 6:
        raise ErrorApi(400, "El día de la semana debe ser un número de 0 (lunes) a 6 (domingo)")
    if fecha_fin < fecha_inicio:
        raise ErrorApi(400, "La fecha de finalización debe ser posterior a la de inicio")
    primera = fecha_inicio + timedelta(days=(dia_semana - fecha_inicio.weekday()) % 7)
    fechas = []
    actual = primera
    while actual <= fecha_fin:
        fechas.append(datetime.combine(actual, hora))
        actual += timedelta(weeks=1)
    if not fechas:
        raise ErrorApi(400, "El período indicado no contiene ninguna ocurrencia de ese día")
    if len(fechas) > MAX_SEMANAS_SERIE:
        raise ErrorApi(400, f"Una franja recurrente no puede superar {MAX_SEMANAS_SERIE} semanas")
    return fechas


def buscar_conflicto(docente_id, inicio, fin, excluir_ids=()):
    consulta = Clase.query.filter(
        Clase.docente_id == docente_id,
        Clase.estado == "activa",
        Clase.inicio < fin,
        Clase.fin > inicio,
    )
    if excluir_ids:
        consulta = consulta.filter(Clase.id.notin_(list(excluir_ids)))
    return consulta.order_by(Clase.inicio).first()


def validar_disponibilidad(docente_id, intervalos, excluir_ids=()):
    """Lanza 409 con la clase en conflicto si algún intervalo se cruza.

    `intervalos` es una lista de (inicio, duracion_min). Para una serie basta
    un conflicto para rechazarla completa: no se crea ninguna ocurrencia.
    """
    for inicio, duracion in intervalos:
        fin = inicio + timedelta(minutes=duracion)
        conflicto = buscar_conflicto(docente_id, inicio, fin, excluir_ids)
        if conflicto is not None:
            raise ErrorApi(
                409, "Ya tienes una clase en ese horario",
                conflicto=conflicto.resumen(),
                fecha=inicio.date().isoformat(),
            )


def bloquear_agenda(docente_id):
    """Serializa las escrituras de un mismo docente para evitar cruces por carrera.

    En MySQL toma un bloqueo de fila sobre el docente; SQLite ya serializa escrituras.
    """
    from .models import Usuario
    if db.engine.dialect.name != "sqlite":
        db.session.query(Usuario).filter(Usuario.id == docente_id).with_for_update().one()
