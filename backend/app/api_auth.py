import re
from datetime import datetime

from flask import Blueprint, g, jsonify, request

from .extensions import db
from .models import ROLES, Usuario
from .seguridad import (ErrorApi, emitir_token, hashear_password, requiere,
                        verificar_password)

bp = Blueprint("auth", __name__, url_prefix="/auth")

EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def _leer_email(valor):
    email = (valor or "").strip().lower() if isinstance(valor, str) else ""
    if not EMAIL.match(email) or len(email) > 120:
        raise ErrorApi(400, "Escribe un correo válido")
    return email


@bp.post("/registro")
def registro():
    datos = request.get_json(silent=True) or {}
    nombre = (datos.get("nombre") or "").strip() if isinstance(datos.get("nombre"), str) else ""
    if not 2 <= len(nombre) <= 80:
        raise ErrorApi(400, "El nombre debe tener entre 2 y 80 caracteres")
    email = _leer_email(datos.get("email"))
    password = datos.get("password")
    if not isinstance(password, str) or not 8 <= len(password) <= 72:
        raise ErrorApi(400, "La contraseña debe tener entre 8 y 72 caracteres")
    rol = datos.get("rol")
    if rol not in ROLES:
        raise ErrorApi(400, "El rol debe ser docente, estudiante o acudiente")
    if datos.get("autoriza_datos") is not True:
        raise ErrorApi(400, "Debes autorizar el tratamiento de tus datos personales")
    if Usuario.query.filter_by(email=email).first():
        raise ErrorApi(409, "Ya existe una cuenta con ese correo")

    usuario = Usuario(nombre=nombre, email=email, rol=rol,
                      password_hash=hashear_password(password),
                      autoriza_datos_en=datetime.utcnow())
    if rol == "estudiante" and datos.get("es_menor") is True:
        # El acudiente se registra primero y es quien autoriza los datos del menor.
        acudiente = Usuario.query.filter_by(
            email=_leer_email(datos.get("acudiente_email")), rol="acudiente").first()
        if acudiente is None:
            raise ErrorApi(400, "Tu acudiente debe crear primero su cuenta con ese correo")
        usuario.es_menor = True
        usuario.acudiente_id = acudiente.id

    db.session.add(usuario)
    db.session.commit()
    return jsonify({"token": emitir_token(usuario), "usuario": usuario.perfil()}), 201


@bp.post("/login")
def login():
    datos = request.get_json(silent=True) or {}
    email = datos.get("email")
    password = datos.get("password")
    usuario = None
    if isinstance(email, str) and isinstance(password, str):
        usuario = Usuario.query.filter_by(email=email.strip().lower()).first()
    if usuario is None or not verificar_password(password, usuario.password_hash):
        raise ErrorApi(401, "Correo o contraseña incorrectos")
    return jsonify({"token": emitir_token(usuario), "usuario": usuario.perfil()})


@bp.get("/me")
@requiere()
def yo():
    return jsonify(g.usuario.perfil())
