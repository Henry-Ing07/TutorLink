"""Avisos escritos por el docente y mensajes privados (ADR-0006)."""
from app.models import BitacoraSeguridad
from conftest import proximo


def preparar(api):
    docente = api.registrar("docente", "Prof. Laura")
    acudiente = api.registrar("acudiente", "Marta")
    mayor = api.registrar("estudiante", "Ana")
    menor = api.registrar("estudiante", "Tomás", es_menor=True, acudiente_email=acudiente["email"])
    clase_id = api.crear_clase(docente, f"{proximo(1)}T14:00", 60, [mayor, menor],
                               "Física").get_json()["id"]
    return docente, acudiente, mayor, menor, clase_id


def anunciar(client, docente, **destino):
    datos = {"titulo": "Traer calculadora", "mensaje": "Mañana hay taller de vectores."}
    datos.update(destino)
    return client.post("/anuncios", headers=docente["h"], json=datos)


# ---------- Avisos del docente ----------

def test_aviso_a_una_clase_llega_a_estudiantes_y_acudiente_del_menor(api, client):
    docente, acudiente, mayor, menor, clase_id = preparar(api)
    r = anunciar(client, docente, clase_id=clase_id)
    assert r.status_code == 201
    assert r.get_json()["anuncio"]["destinatarios"] == 3

    for usuario in (mayor, menor):
        aviso = api.avisos(usuario)[0]
        assert aviso["tipo"] == "anuncio"
        assert aviso["titulo"] == "Prof. Laura: Traer calculadora"
        assert aviso["remitente_id"] == docente["id"]
    assert api.avisos(acudiente)[0]["mensaje"].startswith("Aviso para Tomás")


def test_aviso_a_estudiantes_elegidos_o_a_todos(api, client):
    docente, acudiente, mayor, menor, _ = preparar(api)
    assert anunciar(client, docente, estudiantes=[mayor["id"]]).get_json()["anuncio"][
        "destinatarios"] == 1
    assert [a["tipo"] for a in api.avisos(menor)] == ["creada"]
    assert anunciar(client, docente, todos=True).get_json()["anuncio"]["destinatarios"] == 3


def test_no_se_avisa_a_estudiantes_ajenos_ni_a_clases_ajenas(api, client):
    docente, _, _, _, clase_id = preparar(api)
    otro = api.registrar("docente")
    extrano = api.registrar("estudiante")
    assert anunciar(client, docente, estudiantes=[extrano["id"]]).status_code == 400
    r = anunciar(client, otro, clase_id=clase_id)
    assert r.status_code == 403 and r.data == b""
    assert anunciar(client, otro, todos=True).status_code == 400  # no tiene estudiantes


def test_aviso_exige_titulo_mensaje_y_destino(api, client):
    docente, *_ = preparar(api)
    assert client.post("/anuncios", headers=docente["h"],
                       json={"titulo": "", "mensaje": "x", "todos": True}).status_code == 400
    assert anunciar(client, docente).status_code == 400


def test_solo_el_docente_envia_avisos_y_ve_los_enviados(api, client):
    docente, _, mayor, _, clase_id = preparar(api)
    anunciar(client, docente, clase_id=clase_id)
    enviados = client.get("/anuncios/enviados", headers=docente["h"]).get_json()
    assert len(enviados) == 1 and enviados[0]["clase"]["materia"] == "Física"
    assert anunciar(client, mayor, todos=True).status_code == 403


# ---------- Mensajes privados ----------

def test_contactos_segun_el_rol(api, client):
    docente, acudiente, mayor, menor, _ = preparar(api)

    def nombres(usuario):
        return {(c["usuario"]["nombre"], c["relacion"])
                for c in client.get("/contactos", headers=usuario["h"]).get_json()}

    assert nombres(docente) == {("Ana", "Estudiante"), ("Marta", "Acudiente de Tomás")}
    assert nombres(mayor) == {("Prof. Laura", "Docente")}
    assert nombres(acudiente) == {("Prof. Laura", "Docente")}
    assert nombres(menor) == set()  # con los menores se habla a través del acudiente


def test_conversacion_docente_estudiante(api, client):
    docente, _, mayor, _, _ = preparar(api)
    r = client.post(f"/mensajes/{mayor['id']}", headers=docente["h"], json={"texto": "Hola Ana"})
    assert r.status_code == 201 and r.get_json()["mio"] is True
    client.post(f"/mensajes/{docente['id']}", headers=mayor["h"], json={"texto": "Hola profe"})

    assert client.get("/mensajes/no-leidos", headers=mayor["h"]).get_json() == {"no_leidos": 1}
    hilo = client.get(f"/mensajes/{docente['id']}", headers=mayor["h"]).get_json()
    assert [(m["texto"], m["mio"]) for m in hilo] == [("Hola Ana", False), ("Hola profe", True)]
    assert client.get("/mensajes/no-leidos", headers=mayor["h"]).get_json() == {"no_leidos": 0}

    contacto = client.get("/contactos", headers=docente["h"]).get_json()[0]
    assert contacto["usuario"]["nombre"] == "Ana" and contacto["no_leidos"] == 1
    assert contacto["ultimo"]["texto"] == "Hola profe"

    nuevos = client.get(f"/mensajes/{mayor['id']}?despues_de={hilo[0]['id']}",
                        headers=docente["h"]).get_json()
    assert [m["texto"] for m in nuevos] == ["Hola profe"]


def test_mensaje_genera_notificacion_que_no_aparece_en_la_pestana_avisos(api, client):
    docente, acudiente, _, _, _ = preparar(api)
    client.post(f"/mensajes/{acudiente['id']}", headers=docente["h"],
                json={"texto": "Tomás avanzó mucho hoy"})
    assert all(a["tipo"] != "mensaje" for a in api.avisos(acudiente))
    todos = client.get("/notificaciones/me?incluir_mensajes=1", headers=acudiente["h"]).get_json()
    assert todos[0]["tipo"] == "mensaje" and todos[0]["titulo"] == "Mensaje de Prof. Laura"
    assert todos[0]["remitente_id"] == docente["id"]


def test_no_se_puede_escribir_ni_leer_fuera_de_los_contactos(api, client):
    docente, _, mayor, menor, _ = preparar(api)
    otro_estudiante = api.registrar("estudiante")
    for r in (
        client.post(f"/mensajes/{menor['id']}", headers=docente["h"], json={"texto": "x"}),
        client.post(f"/mensajes/{docente['id']}", headers=menor["h"], json={"texto": "x"}),
        client.post(f"/mensajes/{mayor['id']}", headers=otro_estudiante["h"], json={"texto": "x"}),
        client.get(f"/mensajes/{docente['id']}", headers=otro_estudiante["h"]),
    ):
        assert r.status_code == 403 and r.data == b""
    assert BitacoraSeguridad.query.count() == 4


def test_mensaje_vacio_o_muy_largo(api, client):
    docente, _, mayor, _, _ = preparar(api)
    url = f"/mensajes/{mayor['id']}"
    assert client.post(url, headers=docente["h"], json={"texto": "  "}).status_code == 400
    assert client.post(url, headers=docente["h"], json={"texto": "a" * 1001}).status_code == 400


# ---------- Acudiente ----------

def test_acudiente_ve_las_clases_de_su_menor(api, client):
    docente, acudiente, mayor, menor, _ = preparar(api)
    api.crear_clase(docente, f"{proximo(2)}T10:00", 60, [mayor], "Inglés")
    clases = client.get("/acudientes/me/clases", headers=acudiente["h"]).get_json()
    assert [(c["materia"], c["estudiante"]) for c in clases] == [("Física", "Tomás")]
    assert client.get("/acudientes/me/clases", headers=mayor["h"]).status_code == 403
