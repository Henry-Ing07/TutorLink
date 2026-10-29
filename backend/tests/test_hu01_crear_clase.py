"""HU-01 → RF-01, RF-02: crear clase y rechazar cruces de horario."""
from datetime import date, timedelta

from app.models import Clase
from conftest import proximo


def test_crea_clase_y_aparece_ordenada_en_la_agenda(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    martes = proximo(1)
    r2 = api.crear_clase(docente, f"{martes}T16:00", estudiantes=[ana], materia="Física")
    r1 = api.crear_clase(docente, f"{martes}T09:00", estudiantes=[ana])
    assert r1.status_code == r2.status_code == 201
    assert isinstance(r1.get_json()["id"], int)

    agenda = client.get(f"/docentes/me/clases?desde={martes}&hasta={martes}",
                        headers=docente["h"]).get_json()
    assert [c["materia"] for c in agenda["clases"]] == ["Matemáticas", "Física"]
    assert agenda["clases"][0]["estudiantes"][0]["id"] == ana["id"]


def test_rechaza_clase_en_el_pasado(api):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    ayer = date.today() - timedelta(days=1)
    r = api.crear_clase(docente, f"{ayer}T10:00", estudiantes=[ana])
    assert r.status_code == 400
    assert r.get_json()["error"] == "No se pueden crear clases en el pasado"
    assert Clase.query.count() == 0


def test_duracion_solo_entre_30_y_240(api):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    dia = proximo(2)
    assert api.crear_clase(docente, f"{dia}T08:00", 29, [ana]).status_code == 400
    assert api.crear_clase(docente, f"{dia}T08:00", 241, [ana]).status_code == 400
    assert api.crear_clase(docente, f"{dia}T08:00", 30, [ana]).status_code == 201
    assert api.crear_clase(docente, f"{dia}T09:00", 240, [ana]).status_code == 201


def test_gherkin_rechaza_cruce_e_informa_la_clase_en_conflicto(api, client):
    """Dado Matemáticas martes 14:00-15:00, cuando creo otra 14:30-15:30, entonces 409."""
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    martes = proximo(1)
    api.crear_clase(docente, f"{martes}T14:00", 60, [ana], "Matemáticas")

    r = api.crear_clase(docente, f"{martes}T14:30", 60, [ana], "Química")
    assert r.status_code == 409
    cuerpo = r.get_json()
    assert cuerpo["error"] == "Ya tienes una clase en ese horario"
    assert cuerpo["conflicto"]["materia"] == "Matemáticas"
    assert cuerpo["conflicto"]["inicio"] == f"{martes}T14:00"
    assert Clase.query.count() == 1


def test_gherkin_bordes_exactos_si_se_permiten(api, client):
    """Dado martes 14:00-15:00, cuando creo 15:00-16:00, entonces se guarda."""
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    martes = proximo(1)
    api.crear_clase(docente, f"{martes}T14:00", 60, [ana])
    assert api.crear_clase(docente, f"{martes}T15:00", 60, [ana]).status_code == 201
    assert api.crear_clase(docente, f"{martes}T13:00", 60, [ana]).status_code == 201

    agenda = client.get(f"/docentes/me/clases?desde={martes}&hasta={martes}",
                        headers=docente["h"]).get_json()["clases"]
    assert [c["inicio"][-5:] for c in agenda] == ["13:00", "14:00", "15:00"]


def test_cruce_solo_cuenta_contra_el_mismo_docente(api):
    uno = api.registrar("docente")
    otro = api.registrar("docente")
    ana = api.registrar("estudiante")
    dia = proximo(3)
    api.crear_clase(uno, f"{dia}T10:00", 60, [ana])
    assert api.crear_clase(otro, f"{dia}T10:00", 60, [ana]).status_code == 201


def test_clase_cancelada_libera_la_franja(api, client):
    docente = api.registrar("docente")
    ana = api.registrar("estudiante")
    dia = proximo(4)
    clase_id = api.crear_clase(docente, f"{dia}T10:00", 60, [ana]).get_json()["id"]
    client.post(f"/clases/{clase_id}/cancelar", headers=docente["h"], json={})
    assert api.crear_clase(docente, f"{dia}T10:00", 60, [ana]).status_code == 201


def test_exige_al_menos_un_estudiante_existente(api):
    docente = api.registrar("docente")
    otro_docente = api.registrar("docente")
    dia = proximo(1)
    assert api.crear_clase(docente, f"{dia}T10:00", 60, []).status_code == 400
    assert api.crear_clase(docente, f"{dia}T10:00", 60, [otro_docente]).status_code == 400


def test_solo_el_docente_puede_crear_clases(api):
    ana = api.registrar("estudiante")
    r = api.crear_clase(ana, f"{proximo(1)}T10:00", 60, [ana])
    assert r.status_code == 403
    assert r.data == b""


def test_sin_token_responde_401(client):
    assert client.post("/clases", json={}).status_code == 401
