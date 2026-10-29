"""HU-04 → RF-05: avisos a estudiantes y acudientes."""
from app.models import Notificacion
from conftest import proximo


class ProveedorCaido:
    def enviar(self, notificacion):
        raise ConnectionError("servicio no disponible")


def preparar(api):
    docente = api.registrar("docente", "Prof. Laura")
    acudiente = api.registrar("acudiente")
    mayor1 = api.registrar("estudiante")
    mayor2 = api.registrar("estudiante")
    menor = api.registrar("estudiante", "Tomás", es_menor=True,
                          acudiente_email=acudiente["email"])
    return docente, acudiente, [mayor1, mayor2, menor]


def test_crear_clase_avisa_a_cada_inscrito(api):
    docente, acudiente, alumnos = preparar(api)
    api.crear_clase(docente, f"{proximo(1)}T14:00", 60, alumnos, "Física")
    for alumno in alumnos:
        avisos = api.avisos(alumno)
        assert len(avisos) == 1 and avisos[0]["tipo"] == "creada"
        assert avisos[0]["titulo"] == "Nueva clase de Física"
    assert "Prof. Laura" in avisos[0]["mensaje"]


def test_gherkin_cambio_de_hora_llega_a_mayores_y_al_acudiente_del_menor(api, client):
    docente, acudiente, alumnos = preparar(api)
    dia = proximo(1)
    clase_id = api.crear_clase(docente, f"{dia}T14:00", 60, alumnos, "Física").get_json()["id"]
    antes = Notificacion.query.count()

    r = client.put(f"/clases/{clase_id}", headers=docente["h"], json={"inicio": f"{dia}T16:00"})
    assert r.status_code == 200 and r.get_json()["avisos_pendientes"] == 0
    assert Notificacion.query.count() - antes == 4  # 3 estudiantes + 1 acudiente

    for alumno in alumnos:
        cambio = [a for a in api.avisos(alumno) if a["tipo"] == "cambio"]
        assert len(cambio) == 1
        assert "Física" in cambio[0]["titulo"]
        assert "14:00 a 15:00" in cambio[0]["mensaje"] and "16:00 a 17:00" in cambio[0]["mensaje"]
    del_acudiente = [a for a in api.avisos(acudiente) if a["tipo"] == "cambio"]
    assert len(del_acudiente) == 1 and del_acudiente[0]["mensaje"].startswith("Aviso para Tomás")


def test_gherkin_guardar_sin_cambios_no_envia_avisos(api, client):
    docente, _, alumnos = preparar(api)
    dia = proximo(1)
    clase_id = api.crear_clase(docente, f"{dia}T14:00", 60, alumnos, "Física").get_json()["id"]
    antes = Notificacion.query.count()
    r = client.put(f"/clases/{clase_id}", headers=docente["h"], json={
        "materia": "Física", "inicio": f"{dia}T14:00", "duracion_min": 60,
        "estudiantes": [a["id"] for a in alumnos]})
    assert r.status_code == 200 and r.get_json()["cambios"] is False
    assert Notificacion.query.count() == antes


def test_reagendar_con_cruce_no_cambia_nada_ni_avisa(api, client):
    """Flujo E1 del caso de uso «Reagendar una clase»."""
    docente, _, alumnos = preparar(api)
    dia = proximo(1)
    api.crear_clase(docente, f"{dia}T10:00", 60, alumnos[:1], "Química")
    clase_id = api.crear_clase(docente, f"{dia}T14:00", 60, alumnos, "Física").get_json()["id"]
    antes = Notificacion.query.count()
    r = client.put(f"/clases/{clase_id}", headers=docente["h"], json={"inicio": f"{dia}T10:30"})
    assert r.status_code == 409 and r.get_json()["conflicto"]["materia"] == "Química"
    assert Notificacion.query.count() == antes
    detalle = client.get(f"/clases/{clase_id}", headers=docente["h"]).get_json()
    assert detalle["inicio"] == f"{dia}T14:00"


def test_reagendar_sobre_su_propio_horario_no_es_cruce(api, client):
    docente, _, alumnos = preparar(api)
    dia = proximo(1)
    clase_id = api.crear_clase(docente, f"{dia}T14:00", 60, alumnos).get_json()["id"]
    r = client.put(f"/clases/{clase_id}", headers=docente["h"], json={"duracion_min": 90})
    assert r.status_code == 200


def test_fallo_del_proveedor_no_revierte_el_cambio_y_deja_avisos_pendientes(app, api, client):
    """Flujo E2: el cambio se confirma y los avisos quedan en cola de reintento."""
    from app.notificaciones import reintentar_pendientes

    docente, _, alumnos = preparar(api)
    dia = proximo(1)
    clase_id = api.crear_clase(docente, f"{dia}T14:00", 60, alumnos).get_json()["id"]

    app.extensions["notificador"] = ProveedorCaido()
    r = client.put(f"/clases/{clase_id}", headers=docente["h"], json={"inicio": f"{dia}T18:00"})
    assert r.status_code == 200
    assert r.get_json()["avisos_pendientes"] == 4
    assert r.get_json()["clase"]["inicio"] == f"{dia}T18:00"
    assert Notificacion.query.filter_by(estado="pendiente").count() == 4

    del app.extensions["notificador"]
    assert reintentar_pendientes() == (4, 0)
    assert Notificacion.query.filter_by(estado="pendiente").count() == 0


def test_cancelar_avisa_y_quitar_estudiante_le_avisa_el_retiro(api, client):
    docente, _, alumnos = preparar(api)
    dia = proximo(2)
    clase_id = api.crear_clase(docente, f"{dia}T14:00", 60, alumnos).get_json()["id"]
    client.put(f"/clases/{clase_id}", headers=docente["h"],
               json={"estudiantes": [alumnos[0]["id"], alumnos[1]["id"]]})
    assert api.avisos(alumnos[2])[0]["tipo"] == "retirado"
    client.post(f"/clases/{clase_id}/cancelar", headers=docente["h"], json={})
    assert api.avisos(alumnos[0])[0]["tipo"] == "cancelada"


def test_solo_se_ven_y_marcan_los_avisos_propios(api, client):
    docente, _, alumnos = preparar(api)
    api.crear_clase(docente, f"{proximo(1)}T14:00", 60, alumnos[:1])
    aviso = api.avisos(alumnos[0])[0]
    assert api.avisos(alumnos[1]) == []
    assert client.post(f"/notificaciones/{aviso['id']}/leida",
                       headers=alumnos[1]["h"]).status_code == 403
    assert client.post(f"/notificaciones/{aviso['id']}/leida",
                       headers=alumnos[0]["h"]).status_code == 204
    assert api.avisos(alumnos[0])[0]["leida"] is True
