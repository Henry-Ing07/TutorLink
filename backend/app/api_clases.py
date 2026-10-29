from datetime import datetime, time, timedelta

from flask import Blueprint, g, jsonify, request

from . import horario, notificaciones
from .extensions import db
from .models import Clase, Serie, Usuario, clase_estudiante
from .seguridad import ErrorApi, ahora, denegar, requiere

bp = Blueprint("clases", __name__)

MAX_ESTUDIANTES = 30
ALCANCES = ("ocurrencia", "serie")


def _leer_estudiantes(valor):
    if not isinstance(valor, list) or not valor:
        raise ErrorApi(400, "Agrega al menos un estudiante a la clase")
    if len(valor) > MAX_ESTUDIANTES:
        raise ErrorApi(400, f"Una clase admite máximo {MAX_ESTUDIANTES} estudiantes")
    if not all(isinstance(i, int) and not isinstance(i, bool) for i in valor):
        raise ErrorApi(400, "Los estudiantes deben enviarse como lista de identificadores")
    ids = set(valor)
    encontrados = Usuario.query.filter(Usuario.id.in_(ids), Usuario.rol == "estudiante").all()
    if len(encontrados) != len(ids):
        raise ErrorApi(400, "Alguno de los estudiantes indicados no existe")
    return encontrados


def _clase_propia(clase_id):
    clase = db.session.get(Clase, clase_id)
    if clase is None or clase.docente_id != g.usuario.id:
        denegar(f"clase:{clase_id}")
    return clase


def _leer_alcance(datos, clase):
    alcance = datos.get("alcance", "ocurrencia")
    if alcance not in ALCANCES:
        raise ErrorApi(400, "El alcance debe ser 'ocurrencia' o 'serie'")
    if alcance == "serie" and clase.serie_id is None:
        raise ErrorApi(400, "Esta clase no pertenece a una franja recurrente")
    return alcance


def _grupo(clase, alcance):
    """La clase sola, o ella y las siguientes ocurrencias activas de su serie."""
    if alcance == "ocurrencia":
        return [clase]
    return (Clase.query
            .filter(Clase.serie_id == clase.serie_id, Clase.estado == "activa",
                    Clase.inicio >= clase.inicio)
            .order_by(Clase.inicio).all())


def _respuesta(cuerpo, pendientes, estado=200):
    cuerpo["avisos_pendientes"] = len(pendientes)
    return jsonify(cuerpo), estado


# ---------- Docente ----------

@bp.post("/clases")
@requiere("docente")
def crear_clase():
    datos = request.get_json(silent=True) or {}
    materia = horario.leer_materia(datos.get("materia"))
    inicio = horario.leer_fecha_hora(datos.get("inicio"))
    duracion = horario.leer_duracion(datos.get("duracion_min"))
    estudiantes = _leer_estudiantes(datos.get("estudiantes"))
    horario.exigir_futuro(inicio)

    horario.bloquear_agenda(g.usuario.id)
    horario.validar_disponibilidad(g.usuario.id, [(inicio, duracion)])

    clase = Clase(docente_id=g.usuario.id, materia=materia, estudiantes=estudiantes)
    clase.programar(inicio, duracion)
    db.session.add(clase)
    db.session.flush()
    pendientes = notificaciones.despachar(
        notificaciones.avisar(clase, "creada", estudiantes))
    return _respuesta({"id": clase.id, "clase": clase.a_dict()}, pendientes, 201)


@bp.post("/series")
@requiere("docente")
def crear_serie():
    datos = request.get_json(silent=True) or {}
    materia = horario.leer_materia(datos.get("materia"))
    hora = horario.leer_hora(datos.get("hora_inicio"))
    duracion = horario.leer_duracion(datos.get("duracion_min"))
    fecha_inicio = horario.leer_fecha(datos.get("fecha_inicio"), "fecha_inicio")
    fecha_fin = horario.leer_fecha(datos.get("fecha_fin"), "fecha_fin")
    estudiantes = _leer_estudiantes(datos.get("estudiantes"))
    dia = datos.get("dia_semana")
    inicios = horario.ocurrencias_semanales(dia, hora, fecha_inicio, fecha_fin)
    horario.exigir_futuro(inicios[0])

    horario.bloquear_agenda(g.usuario.id)
    horario.validar_disponibilidad(g.usuario.id, [(i, duracion) for i in inicios])

    serie = Serie(docente_id=g.usuario.id, materia=materia, dia_semana=dia, hora_inicio=hora,
                  duracion_min=duracion, fecha_inicio=fecha_inicio, fecha_fin=fecha_fin)
    db.session.add(serie)
    db.session.flush()
    clases = []
    for inicio in inicios:
        clase = Clase(docente_id=g.usuario.id, serie_id=serie.id, materia=materia,
                      estudiantes=estudiantes)
        clase.programar(inicio, duracion)
        clases.append(clase)
    db.session.add_all(clases)
    db.session.flush()
    avisos = notificaciones.avisar(clases[0], "creada", estudiantes,
                                   sufijo=f" Se repite cada semana ({len(clases)} clases).")
    pendientes = notificaciones.despachar(avisos)
    return _respuesta({"serie_id": serie.id, "clases": [c.resumen() for c in clases]},
                      pendientes, 201)


@bp.get("/docentes/me/clases")
@requiere("docente")
def agenda_docente():
    hoy = ahora().date()
    desde = horario.leer_fecha(request.args.get("desde", hoy.isoformat()), "desde")
    hasta = horario.leer_fecha(request.args.get("hasta", (desde + timedelta(days=6)).isoformat()),
                               "hasta")
    if hasta < desde or (hasta - desde).days > 62:
        raise ErrorApi(400, "El rango de fechas debe ser de máximo 62 días")
    clases = (Clase.query
              .filter(Clase.docente_id == g.usuario.id, Clase.estado == "activa",
                      Clase.inicio >= datetime.combine(desde, time.min),
                      Clase.inicio < datetime.combine(hasta + timedelta(days=1), time.min))
              .order_by(Clase.inicio).all())
    return jsonify({"desde": desde.isoformat(), "hasta": hasta.isoformat(),
                    "clases": [c.a_dict() for c in clases]})


@bp.get("/docentes/me/estudiantes")
@requiere("docente")
def estudiantes_del_docente():
    """Estudiantes que ya estuvieron en alguna clase del docente (para agregarlos rápido)."""
    estudiantes = (Usuario.query
                   .join(clase_estudiante, clase_estudiante.c.estudiante_id == Usuario.id)
                   .join(Clase, Clase.id == clase_estudiante.c.clase_id)
                   .filter(Clase.docente_id == g.usuario.id)
                   .distinct().order_by(Usuario.nombre).all())
    return jsonify([e.resumen() for e in estudiantes])


@bp.get("/estudiantes/buscar")
@requiere("docente")
def buscar_estudiante():
    """Búsqueda por correo exacto: no se expone el directorio de estudiantes."""
    email = (request.args.get("email") or "").strip().lower()
    estudiante = Usuario.query.filter_by(email=email, rol="estudiante").first()
    if estudiante is None:
        raise ErrorApi(404, "No hay ningún estudiante registrado con ese correo")
    return jsonify(estudiante.resumen())


@bp.put("/clases/<int:clase_id>")
@requiere("docente")
def reagendar(clase_id):
    """Caso de uso «Reagendar una clase» (FPI-06)."""
    clase = _clase_propia(clase_id)
    datos = request.get_json(silent=True) or {}
    if clase.estado != "activa":
        raise ErrorApi(400, "La clase está cancelada")
    if clase.fin <= ahora():
        raise ErrorApi(400, "La clase ya finalizó y no se puede modificar")
    alcance = _leer_alcance(datos, clase)

    materia = horario.leer_materia(datos["materia"]) if "materia" in datos else clase.materia
    inicio = horario.leer_fecha_hora(datos["inicio"]) if "inicio" in datos else clase.inicio
    duracion = (horario.leer_duracion(datos["duracion_min"]) if "duracion_min" in datos
                else clase.duracion_min)
    estudiantes = (_leer_estudiantes(datos["estudiantes"]) if "estudiantes" in datos
                   else list(clase.estudiantes))

    grupo = _grupo(clase, alcance)
    desfase = inicio - clase.inicio
    nuevos = [(c, c.inicio + desfase) for c in grupo]
    cambia_horario = desfase != timedelta(0) or duracion != clase.duracion_min
    if cambia_horario:
        horario.exigir_futuro(inicio)
        horario.bloquear_agenda(g.usuario.id)
        horario.validar_disponibilidad(g.usuario.id, [(n, duracion) for _, n in nuevos],
                                       excluir_ids=[c.id for c in grupo])

    anteriores = set(clase.estudiantes)
    actuales = set(estudiantes)
    if not cambia_horario and materia == clase.materia and anteriores == actuales:
        # HU-04, caso límite: sin cambio real no se envía ningún aviso.
        return _respuesta({"clase": clase.a_dict(), "cambios": False}, [])

    anterior = (clase.inicio, clase.fin)
    for c, nuevo_inicio in nuevos:
        c.materia = materia
        c.programar(nuevo_inicio, duracion)
        c.estudiantes = estudiantes
    if alcance == "serie" and clase.serie is not None:
        clase.serie.materia = materia
    db.session.flush()

    sufijo = "" if len(grupo) == 1 else \
        f" Aplica también a las siguientes {len(grupo) - 1} clases."
    siguen = [e for e in estudiantes if e in anteriores]
    nuevos_alumnos = [e for e in estudiantes if e not in anteriores]
    retirados = [e for e in anteriores if e not in actuales]
    avisos = []
    if cambia_horario:
        avisos += notificaciones.avisar(clase, "cambio", siguen, anterior=anterior, sufijo=sufijo)
    avisos += notificaciones.avisar(clase, "creada", nuevos_alumnos, sufijo=sufijo)
    avisos += notificaciones.avisar(clase, "retirado", retirados, sufijo=sufijo)
    pendientes = notificaciones.despachar(avisos)
    return _respuesta({"clase": clase.a_dict(), "cambios": True,
                       "clases_afectadas": len(grupo)}, pendientes)


@bp.post("/clases/<int:clase_id>/cancelar")
@requiere("docente")
def cancelar(clase_id):
    clase = _clase_propia(clase_id)
    datos = request.get_json(silent=True) or {}
    if clase.estado != "activa":
        raise ErrorApi(400, "La clase ya estaba cancelada")
    alcance = _leer_alcance(datos, clase)
    grupo = _grupo(clase, alcance)
    for c in grupo:
        c.estado = "cancelada"
    db.session.flush()
    sufijo = "" if len(grupo) == 1 else \
        f" También se cancelan las siguientes {len(grupo) - 1} clases de la franja."
    pendientes = notificaciones.despachar(
        notificaciones.avisar(clase, "cancelada", list(clase.estudiantes), sufijo=sufijo))
    return _respuesta({"canceladas": [c.id for c in grupo]}, pendientes)


# ---------- Docente o estudiante inscrito ----------

@bp.get("/clases/<int:clase_id>")
@requiere()
def detalle(clase_id):
    clase = db.session.get(Clase, clase_id)
    usuario = g.usuario
    if clase is None:
        denegar(f"clase:{clase_id}")
    if usuario.rol == "docente" and clase.docente_id == usuario.id:
        return jsonify(clase.a_dict())
    if usuario.rol == "estudiante" and usuario in clase.estudiantes:
        # El estudiante no ve los datos de sus compañeros (hay menores de edad).
        return jsonify(clase.a_dict(con_estudiantes=False))
    denegar(f"clase:{clase_id}")


# ---------- Estudiante ----------

@bp.get("/estudiantes/me/clases")
@requiere("estudiante")
def clases_del_estudiante():
    """RF-04: se filtra por el usuario del token, nunca por un parámetro (ADR-0003)."""
    clases = (Clase.query
              .join(clase_estudiante, clase_estudiante.c.clase_id == Clase.id)
              .filter(clase_estudiante.c.estudiante_id == g.usuario.id,
                      Clase.estado == "activa", Clase.fin >= ahora())
              .order_by(Clase.inicio).all())
    return jsonify([c.a_dict(con_estudiantes=False) for c in clases])
