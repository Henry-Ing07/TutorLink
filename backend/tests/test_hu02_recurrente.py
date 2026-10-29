"""HU-02 → RF-03: franja recurrente semanal."""
from datetime import timedelta

from app.models import Clase
from conftest import proximo


def crear_serie(client, docente, estudiantes, inicio, semanas=8, dia=3, hora="16:00"):
    return client.post("/series", headers=docente["h"], json={
        "materia": "Física", "dia_semana": dia, "hora_inicio": hora, "duracion_min": 60,
        "fecha_inicio": inicio.isoformat(),
        "fecha_fin": (inicio + timedelta(weeks=semanas - 1)).isoformat(),
        "estudiantes": [e["id"] for e in estudiantes]})


def test_genera_exactamente_8_ocurrencias(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    r = crear_serie(client, docente, [ana], proximo(3))
    assert r.status_code == 201
    clases = r.get_json()["clases"]
    assert len(clases) == 8
    assert all(c["inicio"].endswith("T16:00") for c in clases)
    assert Clase.query.filter_by(serie_id=r.get_json()["serie_id"]).count() == 8


def test_gherkin_cancelar_una_ocurrencia_no_afecta_las_demas(api, client):
    """Cuando cancelo la tercera semana, las otras 7 siguen igual."""
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    clases = crear_serie(client, docente, [ana], proximo(3)).get_json()["clases"]
    tercera = clases[2]

    r = client.post(f"/clases/{tercera['id']}/cancelar", headers=docente["h"],
                    json={"alcance": "ocurrencia"})
    assert r.status_code == 200 and r.get_json()["canceladas"] == [tercera["id"]]

    vistas = client.get("/estudiantes/me/clases", headers=ana["h"]).get_json()
    assert len(vistas) == 7
    assert tercera["id"] not in [c["id"] for c in vistas]
    restantes = [c for c in clases if c["id"] != tercera["id"]]
    assert [(c["id"], c["inicio"]) for c in vistas] == [(c["id"], c["inicio"]) for c in restantes]


def test_gherkin_un_cruce_en_la_serie_impide_crear_toda_la_serie(api, client):
    """Con una clase existente un jueves a las 16:30, la serie de 16:00 no se crea."""
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    inicio = proximo(3)
    choque = inicio + timedelta(weeks=2)
    api.crear_clase(docente, f"{choque}T16:30", 60, [ana], "Química")

    r = crear_serie(client, docente, [ana], inicio)
    assert r.status_code == 409
    assert r.get_json()["fecha"] == choque.isoformat()
    assert r.get_json()["conflicto"]["materia"] == "Química"
    assert Clase.query.count() == 1


def test_reagendar_solo_una_ocurrencia(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    clases = crear_serie(client, docente, [ana], proximo(3)).get_json()["clases"]
    segunda = clases[1]
    nueva = segunda["inicio"].replace("T16:00", "T18:00")
    r = client.put(f"/clases/{segunda['id']}", headers=docente["h"],
                   json={"inicio": nueva, "alcance": "ocurrencia"})
    assert r.status_code == 200 and r.get_json()["clases_afectadas"] == 1
    vistas = client.get("/estudiantes/me/clases", headers=ana["h"]).get_json()
    horas = [c["inicio"][-5:] for c in vistas]
    assert horas.count("18:00") == 1 and horas.count("16:00") == 7


def test_reagendar_la_serie_mueve_las_ocurrencias_siguientes(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    clases = crear_serie(client, docente, [ana], proximo(3)).get_json()["clases"]
    cuarta = clases[3]
    r = client.put(f"/clases/{cuarta['id']}", headers=docente["h"], json={
        "inicio": cuarta["inicio"].replace("T16:00", "T17:00"), "alcance": "serie"})
    assert r.status_code == 200 and r.get_json()["clases_afectadas"] == 5
    vistas = client.get("/estudiantes/me/clases", headers=ana["h"]).get_json()
    horas = [c["inicio"][-5:] for c in vistas]
    assert horas == ["16:00"] * 3 + ["17:00"] * 5
    # Un solo aviso para toda la serie, no uno por ocurrencia.
    cambios = [a for a in api.avisos(ana) if a["tipo"] == "cambio"]
    assert len(cambios) == 1 and "siguientes 4 clases" in cambios[0]["mensaje"]


def test_cancelar_la_serie_desde_una_ocurrencia(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    clases = crear_serie(client, docente, [ana], proximo(3)).get_json()["clases"]
    r = client.post(f"/clases/{clases[5]['id']}/cancelar", headers=docente["h"],
                    json={"alcance": "serie"})
    assert len(r.get_json()["canceladas"]) == 3
    assert len(client.get("/estudiantes/me/clases", headers=ana["h"]).get_json()) == 5


def test_alcance_serie_en_clase_suelta_es_error(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    clase_id = api.crear_clase(docente, f"{proximo(1)}T10:00", 60, [ana]).get_json()["id"]
    r = client.post(f"/clases/{clase_id}/cancelar", headers=docente["h"],
                    json={"alcance": "serie"})
    assert r.status_code == 400
