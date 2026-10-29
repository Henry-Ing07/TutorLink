"""Avisos escritos por el docente y mensajes privados (cambio de alcance, ADR-0006)."""
from flask import Blueprint, g, jsonify, request
from sqlalchemy import and_, func, or_

from . import notificaciones
from .contactos import contactos_de, estudiantes_del_docente, puede_conversar
from .extensions import db
from .models import Anuncio, Clase, Mensaje, Usuario, clase_estudiante
from .seguridad import ErrorApi, ahora, denegar, requiere

bp = Blueprint("comunicacion", __name__)


def _texto(valor, campo, maximo, obligatorio=True):
    texto = valor.strip() if isinstance(valor, str) else ""
    if obligatorio and not texto:
        raise ErrorApi(400, f"Escribe {campo}")
    if len(texto) > maximo:
        raise ErrorApi(400, f"{campo.capitalize()} no puede superar {maximo} caracteres")
    return texto


# ---------- Avisos del docente ----------

@bp.post("/anuncios")
@requiere("docente")
def crear_anuncio():
    """Destino: una clase propia (`clase_id`), estudiantes propios (`estudiantes`)
    o todos sus estudiantes (`todos: true`)."""
    datos = request.get_json(silent=True) or {}
    titulo = _texto(datos.get("titulo"), "el título", 80)
    mensaje = _texto(datos.get("mensaje"), "el mensaje", 400)
    mios = {e.id: e for e in estudiantes_del_docente(g.usuario.id)}
    clase = None

    if datos.get("clase_id") is not None:
        clase = db.session.get(Clase, datos["clase_id"]) \
            if isinstance(datos["clase_id"], int) else None
        if clase is None or clase.docente_id != g.usuario.id:
            denegar(f"clase:{datos['clase_id']}")
        estudiantes = list(clase.estudiantes)
    elif datos.get("todos") is True:
        estudiantes = list(mios.values())
    else:
        ids = datos.get("estudiantes")
        if not isinstance(ids, list) or not ids:
            raise ErrorApi(400, "Elige una clase, algunos estudiantes o todos")
        if not all(isinstance(i, int) and i in mios for i in ids):
            # Solo a estudiantes que están o estuvieron en sus clases.
            raise ErrorApi(400, "Solo puedes enviar avisos a tus estudiantes")
        estudiantes = [mios[i] for i in set(ids)]
    if not estudiantes:
        raise ErrorApi(400, "Ese destino no tiene estudiantes")

    anuncio = Anuncio(docente_id=g.usuario.id, clase_id=clase.id if clase else None,
                      titulo=titulo, mensaje=mensaje)
    db.session.add(anuncio)
    db.session.flush()
    avisos = notificaciones.avisar_anuncio(anuncio, g.usuario, estudiantes)
    anuncio.destinatarios = len(avisos)
    pendientes = notificaciones.despachar(avisos)
    return jsonify({"anuncio": anuncio.a_dict(),
                    "avisos_pendientes": len(pendientes)}), 201


@bp.get("/anuncios/enviados")
@requiere("docente")
def anuncios_enviados():
    anuncios = (Anuncio.query.filter_by(docente_id=g.usuario.id)
                .order_by(Anuncio.id.desc()).limit(50).all())
    return jsonify([a.a_dict() for a in anuncios])


# ---------- Mensajes privados ----------

def _entre(a, b):
    return or_(and_(Mensaje.remitente_id == a, Mensaje.destinatario_id == b),
               and_(Mensaje.remitente_id == b, Mensaje.destinatario_id == a))


@bp.get("/contactos")
@requiere()
def contactos():
    """Con quién puede conversar el usuario, con el último mensaje y los no leídos."""
    yo = g.usuario.id
    lista = []
    for otro, relacion in contactos_de(g.usuario).values():
        ultimo = (Mensaje.query.filter(_entre(yo, otro.id))
                  .order_by(Mensaje.id.desc()).first())
        no_leidos = Mensaje.query.filter_by(remitente_id=otro.id, destinatario_id=yo,
                                            leido=False).count()
        lista.append({
            "usuario": {"id": otro.id, "nombre": otro.nombre, "rol": otro.rol},
            "relacion": relacion,
            "ultimo": ultimo.a_dict(yo) if ultimo else None,
            "no_leidos": no_leidos,
        })
    lista.sort(key=lambda c: (c["ultimo"] is None, -(c["ultimo"] or {}).get("id", 0),
                              c["usuario"]["nombre"]))
    return jsonify(lista)


@bp.get("/mensajes/no-leidos")
@requiere()
def no_leidos():
    total = (db.session.query(func.count(Mensaje.id))
             .filter_by(destinatario_id=g.usuario.id, leido=False).scalar())
    return jsonify({"no_leidos": total})


@bp.get("/mensajes/<int:otro_id>")
@requiere()
def conversacion(otro_id):
    if not puede_conversar(g.usuario, otro_id):
        denegar(f"conversacion:{otro_id}")
    yo = g.usuario.id
    consulta = Mensaje.query.filter(_entre(yo, otro_id))
    despues_de = request.args.get("despues_de", type=int)
    if despues_de:
        consulta = consulta.filter(Mensaje.id > despues_de)
    mensajes = list(reversed(consulta.order_by(Mensaje.id.desc()).limit(100).all()))
    Mensaje.query.filter_by(remitente_id=otro_id, destinatario_id=yo, leido=False) \
        .update({"leido": True})
    db.session.commit()
    return jsonify([m.a_dict(yo) for m in mensajes])


@bp.post("/mensajes/<int:otro_id>")
@requiere()
def enviar_mensaje(otro_id):
    if not puede_conversar(g.usuario, otro_id):
        denegar(f"conversacion:{otro_id}")
    datos = request.get_json(silent=True) or {}
    texto = _texto(datos.get("texto"), "el mensaje", 1000)
    mensaje = Mensaje(remitente_id=g.usuario.id, destinatario_id=otro_id, texto=texto)
    db.session.add(mensaje)
    db.session.flush()
    notificaciones.despachar(notificaciones.avisar_mensaje(mensaje, g.usuario))
    return jsonify(mensaje.a_dict(g.usuario.id)), 201


# ---------- Acudiente ----------

@bp.get("/acudientes/me/clases")
@requiere("acudiente")
def clases_de_mis_menores():
    """Próximas clases de los menores a cargo del acudiente (solo lectura)."""
    filas = (db.session.query(Clase, Usuario)
             .join(clase_estudiante, clase_estudiante.c.clase_id == Clase.id)
             .join(Usuario, Usuario.id == clase_estudiante.c.estudiante_id)
             .filter(Usuario.acudiente_id == g.usuario.id, Clase.estado == "activa",
                     Clase.fin >= ahora())
             .order_by(Clase.inicio).all())
    resultado = []
    for clase, menor in filas:
        datos = clase.a_dict(con_estudiantes=False)
        datos["estudiante"] = menor.nombre
        resultado.append(datos)
    return jsonify(resultado)
