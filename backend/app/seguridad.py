"""Autenticación, autorización y utilidades de error (RNF-03)."""
from datetime import datetime, timedelta, timezone
from functools import wraps

import bcrypt
import jwt
from flask import current_app, g, jsonify, request

from .extensions import db
from .models import BitacoraSeguridad, Usuario


class ErrorApi(Exception):
    def __init__(self, estado, mensaje, **extra):
        super().__init__(mensaje)
        self.estado = estado
        self.mensaje = mensaje
        self.extra = extra


def ahora():
    """Hora local de operación, sin zona, igual que las fechas guardadas."""
    desfase = timedelta(hours=current_app.config["UTC_OFFSET_HORAS"])
    return datetime.now(timezone.utc).replace(tzinfo=None) + desfase


def hashear_password(password):
    sal = bcrypt.gensalt(rounds=current_app.config["BCRYPT_ROUNDS"])
    return bcrypt.hashpw(password.encode("utf-8"), sal).decode("ascii")


def verificar_password(password, password_hash):
    return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("ascii"))


def emitir_token(usuario):
    expira = datetime.now(timezone.utc) + timedelta(hours=current_app.config["TOKEN_HORAS"])
    datos = {"sub": str(usuario.id), "rol": usuario.rol, "exp": expira}
    return jwt.encode(datos, current_app.config["SECRET_KEY"], algorithm="HS256")


def _usuario_del_token():
    cabecera = request.headers.get("Authorization", "")
    if not cabecera.startswith("Bearer "):
        raise ErrorApi(401, "Debes iniciar sesión")
    try:
        datos = jwt.decode(cabecera[7:], current_app.config["SECRET_KEY"], algorithms=["HS256"])
    except jwt.ExpiredSignatureError:
        raise ErrorApi(401, "Tu sesión expiró, vuelve a iniciar sesión")
    except jwt.InvalidTokenError:
        raise ErrorApi(401, "Sesión inválida")
    usuario = db.session.get(Usuario, int(datos["sub"]))
    if usuario is None:
        raise ErrorApi(401, "Sesión inválida")
    return usuario


def requiere(*roles):
    """Exige token válido y, si se indican, uno de los roles permitidos.

    El usuario sale siempre del token, nunca de un parámetro del cliente (ADR-0003).
    """
    def decorador(vista):
        @wraps(vista)
        def envoltura(*args, **kwargs):
            g.usuario = _usuario_del_token()
            if roles and g.usuario.rol not in roles:
                denegar(f"{request.method} {request.path}")
            return vista(*args, **kwargs)
        return envoltura
    return decorador


def denegar(recurso):
    """Registra el intento en la bitácora y responde 403 sin cuerpo."""
    usuario_id = g.usuario.id if "usuario" in g else None
    db.session.add(BitacoraSeguridad(usuario_id=usuario_id, evento="acceso_denegado",
                                     recurso=recurso[:120]))
    db.session.commit()
    raise ErrorApi(403, "")


def registrar_manejadores(app):
    @app.errorhandler(ErrorApi)
    def manejar_error_api(error):
        if error.estado == 403:
            return "", 403
        cuerpo = {"error": error.mensaje}
        cuerpo.update(error.extra)
        return jsonify(cuerpo), error.estado

    @app.errorhandler(404)
    def no_encontrado(_error):
        return jsonify({"error": "Recurso no encontrado"}), 404

    @app.errorhandler(405)
    def metodo_no_permitido(_error):
        return jsonify({"error": "Método no permitido"}), 405

    @app.errorhandler(500)
    def error_interno(_error):
        db.session.rollback()
        return jsonify({"error": "Error interno del servidor"}), 500
