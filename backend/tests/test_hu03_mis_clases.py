"""HU-03 → RF-04, RNF-03: cada estudiante ve solo sus clases."""
from datetime import timedelta

from app.models import BitacoraSeguridad
from conftest import proximo


def test_gherkin_ve_exactamente_sus_3_de_10_clases_ordenadas(api, client):
    docente = api.registrar("docente")
    yo = api.registrar("estudiante")
    otro = api.registrar("estudiante")
    base = proximo(0)
    mias = {2, 5, 7}
    ids = []
    for i in reversed(range(10)):  # se crean en desorden a propósito
        alumnos = [yo] if i in mias else [otro]
        r = api.crear_clase(docente, f"{base + timedelta(days=i)}T10:00", 60, alumnos,
                            f"Materia {i}")
        if i in mias:
            ids.append(r.get_json()["id"])

    vistas = client.get("/estudiantes/me/clases", headers=yo["h"]).get_json()
    assert len(vistas) == 3
    assert [c["materia"] for c in vistas] == ["Materia 2", "Materia 5", "Materia 7"]
    primera = vistas[0]
    assert set(primera) >= {"materia", "inicio", "duracion_min", "docente"}
    assert primera["docente"]["nombre"]
    assert "estudiantes" not in primera  # no se exponen datos de compañeros


def test_sin_clases_devuelve_lista_vacia(api, client):
    ana = api.registrar("estudiante")
    r = client.get("/estudiantes/me/clases", headers=ana["h"])
    assert r.status_code == 200 and r.get_json() == []


def test_gherkin_acceso_a_clase_ajena_es_403_sin_datos_y_queda_en_bitacora(api, client):
    docente = api.registrar("docente")
    inscrito = api.registrar("estudiante")
    intruso = api.registrar("estudiante")
    clase_id = api.crear_clase(docente, f"{proximo(1)}T10:00", 60, [inscrito]).get_json()["id"]

    r = client.get(f"/clases/{clase_id}", headers=intruso["h"])
    assert r.status_code == 403
    assert r.data == b""
    registro = BitacoraSeguridad.query.one()
    assert registro.usuario_id == intruso["id"] and registro.recurso == f"clase:{clase_id}"

    assert client.get(f"/clases/{clase_id}", headers=inscrito["h"]).status_code == 200


def test_docente_no_ve_ni_modifica_clases_de_otro_docente(api, client):
    duena = api.registrar("docente")
    ajena = api.registrar("docente")
    ana = api.registrar("estudiante")
    clase_id = api.crear_clase(duena, f"{proximo(1)}T10:00", 60, [ana]).get_json()["id"]
    for r in (client.get(f"/clases/{clase_id}", headers=ajena["h"]),
              client.put(f"/clases/{clase_id}", headers=ajena["h"], json={"duracion_min": 90}),
              client.post(f"/clases/{clase_id}/cancelar", headers=ajena["h"], json={})):
        assert r.status_code == 403 and r.data == b""


def test_clase_inexistente_tambien_es_403(api, client):
    ana = api.registrar("estudiante")
    assert client.get("/clases/999", headers=ana["h"]).status_code == 403


def test_el_filtro_sale_del_token_y_no_de_parametros(api, client):
    docente = api.registrar("docente")
    yo = api.registrar("estudiante")
    otro = api.registrar("estudiante")
    api.crear_clase(docente, f"{proximo(1)}T10:00", 60, [otro])
    r = client.get(f"/estudiantes/me/clases?estudiante_id={otro['id']}", headers=yo["h"])
    assert r.get_json() == []


def test_docente_no_usa_endpoint_de_estudiante(api, client):
    docente = api.registrar("docente")
    assert client.get("/estudiantes/me/clases", headers=docente["h"]).status_code == 403
