"""RNF-03 y restricción de datos de menores (Ley 1581 de 2012)."""
from datetime import datetime, timedelta, timezone

import jwt

from app.config import Config
from app.models import BitacoraSeguridad, Usuario
from conftest import proximo


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


# ---------- Token que solo sirve para avisos (ADR-0008) ----------

def _con_token_avisos(client, api):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    api.crear_clase(docente, f"{proximo(1)}T10:00", 60, [ana], "Física")
    r = client.post("/auth/login", json={"email": ana["email"], "password": "clave-segura"})
    return docente, ana, {"Authorization": f"Bearer {r.get_json()['token_avisos']}"}


def test_login_y_registro_entregan_token_de_avisos(api, client):
    ana = api.registrar("estudiante")
    r = client.post("/auth/login", json={"email": ana["email"], "password": "clave-segura"})
    datos = r.get_json()
    assert datos["token"] and datos["token_avisos"] and datos["token"] != datos["token_avisos"]
    registro = client.post("/auth/registro", json={
        "nombre": "Nuevo", "email": "nuevo@prueba.co", "password": "clave-segura",
        "rol": "docente", "autoriza_datos": True})
    assert registro.get_json()["token_avisos"]


def test_token_de_avisos_descarga_la_bandeja(api, client):
    _, _, avisos = _con_token_avisos(client, api)
    r = client.get("/notificaciones/me?incluir_mensajes=1", headers=avisos)
    assert r.status_code == 200
    assert r.get_json()[0]["titulo"] == "Nueva clase de Física"


def test_token_de_avisos_no_sirve_para_nada_mas(api, client):
    docente, ana, avisos = _con_token_avisos(client, api)
    clase_id = client.get("/estudiantes/me/clases", headers=ana["h"]).get_json()[0]["id"]
    aviso_id = api.avisos(ana)[0]["id"]
    for metodo, ruta, cuerpo in [
        ("get", "/auth/me", None),
        ("get", "/estudiantes/me/clases", None),
        ("get", f"/clases/{clase_id}", None),
        ("get", "/contactos", None),
        ("get", f"/mensajes/{docente['id']}", None),
        ("post", f"/mensajes/{docente['id']}", {"texto": "hola"}),
        ("post", f"/notificaciones/{aviso_id}/leida", None),
    ]:
        r = getattr(client, metodo)(ruta, headers=avisos, json=cuerpo)
        assert r.status_code == 403 and r.data == b"", ruta
    recurso = BitacoraSeguridad.recurso
    assert BitacoraSeguridad.query.filter(recurso.contains("token de avisos")).count() == 7


def test_docente_con_token_de_avisos_no_puede_crear_clases(api, client):
    docente = api.registrar("docente")
    r = client.post("/auth/login", json={"email": docente["email"], "password": "clave-segura"})
    avisos = {"Authorization": f"Bearer {r.get_json()['token_avisos']}"}
    assert api.crear_clase({"h": avisos}, f"{proximo(2)}T10:00", 60, []).status_code == 403


def test_token_anterior_sin_alcance_sigue_funcionando_como_sesion(app, api, client):
    ana = api.registrar("estudiante")
    viejo = jwt.encode({"sub": str(ana["id"]), "rol": "estudiante",
                        "exp": datetime.now(timezone.utc) + timedelta(hours=1)},
                       app.config["SECRET_KEY"], algorithm="HS256")
    assert client.get("/auth/me", headers={"Authorization": f"Bearer {viejo}"}).status_code == 200


def test_alcance_desconocido_es_rechazado(app, api, client):
    ana = api.registrar("estudiante")
    raro = jwt.encode({"sub": str(ana["id"]), "rol": "estudiante", "alcance": "admin",
                       "exp": datetime.now(timezone.utc) + timedelta(hours=1)},
                      app.config["SECRET_KEY"], algorithm="HS256")
    assert client.get("/auth/me", headers={"Authorization": f"Bearer {raro}"}).status_code == 401
