"""RNF-03 y restricción de datos de menores (Ley 1581 de 2012)."""
from app.config import Config
from app.models import Usuario


def test_bcrypt_por_defecto_usa_costo_12():
    assert Config.BCRYPT_ROUNDS >= 12


def test_la_contrasena_se_guarda_como_hash_bcrypt(api):
    api.registrar("docente")
    usuario = Usuario.query.one()
    assert usuario.password_hash.startswith("$2b$")
    assert "clave-segura" not in usuario.password_hash


def test_login_correcto_e_incorrecto(api, client):
    docente = api.registrar("docente")
    ok = client.post("/auth/login", json={"email": docente["email"], "password": "clave-segura"})
    assert ok.status_code == 200 and ok.get_json()["usuario"]["rol"] == "docente"
    mal = client.post("/auth/login", json={"email": docente["email"], "password": "otra-clave"})
    assert mal.status_code == 401


def test_token_alterado_es_rechazado(api, client):
    docente = api.registrar("docente")
    token = docente["h"]["Authorization"] + "x"
    assert client.get("/auth/me", headers={"Authorization": token}).status_code == 401


def test_registro_exige_autorizacion_de_datos(client):
    r = client.post("/auth/registro", json={"nombre": "Ana", "email": "a@b.co",
                                            "password": "clave-segura", "rol": "estudiante"})
    assert r.status_code == 400


def test_menor_requiere_acudiente_registrado(api, client):
    datos = {"nombre": "Tomás", "email": "t@b.co", "password": "clave-segura",
             "rol": "estudiante", "autoriza_datos": True, "es_menor": True,
             "acudiente_email": "noexiste@b.co"}
    assert client.post("/auth/registro", json=datos).status_code == 400
    acudiente = api.registrar("acudiente")
    datos["acudiente_email"] = acudiente["email"]
    assert client.post("/auth/registro", json=datos).status_code == 201


def test_correo_duplicado(api, client):
    docente = api.registrar("docente")
    r = client.post("/auth/registro", json={"nombre": "Otra", "email": docente["email"],
                                            "password": "clave-segura", "rol": "docente",
                                            "autoriza_datos": True})
    assert r.status_code == 409


def test_buscar_estudiante_solo_por_correo_exacto_y_solo_docentes(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    r = client.get(f"/estudiantes/buscar?email={ana['email']}", headers=docente["h"])
    assert r.status_code == 200 and r.get_json()["id"] == ana["id"]
    parcial = client.get("/estudiantes/buscar?email=estudiante", headers=docente["h"])
    assert parcial.status_code == 404
    assert client.get(f"/estudiantes/buscar?email={ana['email']}",
                      headers=ana["h"]).status_code == 403
