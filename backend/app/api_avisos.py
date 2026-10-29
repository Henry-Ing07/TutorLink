from flask import Blueprint, g, jsonify, request

from .extensions import db
from .models import Notificacion
from .seguridad import denegar, requiere

bp = Blueprint("avisos", __name__, url_prefix="/notificaciones")


@bp.get("/me")
@requiere()
def mis_avisos():
    """Bandeja del usuario autenticado. `despues_de` permite descargar solo lo nuevo."""
    consulta = Notificacion.query.filter_by(destinatario_id=g.usuario.id)
    despues_de = request.args.get("despues_de", type=int)
    if despues_de:
        consulta = consulta.filter(Notificacion.id > despues_de)
    avisos = consulta.order_by(Notificacion.id.desc()).limit(50).all()
    return jsonify([a.a_dict() for a in avisos])


@bp.post("/<int:aviso_id>/leida")
@requiere()
def marcar_leida(aviso_id):
    aviso = db.session.get(Notificacion, aviso_id)
    if aviso is None or aviso.destinatario_id != g.usuario.id:
        denegar(f"notificacion:{aviso_id}")
    aviso.leida = True
    db.session.commit()
    return "", 204
