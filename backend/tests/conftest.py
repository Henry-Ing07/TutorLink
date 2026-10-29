from datetime import date, timedelta

import pytest

from app import create_app
from app.extensions import db


@pytest.fixture
def app():
    app = create_app({
        "TESTING": True,
        "SECRET_KEY": "clave-solo-para-pruebas-con-32-caracteres-o-mas",
        "SQLALCHEMY_DATABASE_URI": "sqlite://",
        "BCRYPT_ROUNDS": 4,  # rapidez en pruebas; producción usa 12 (test_seguridad)
    })
    with app.app_context():
        db.create_all()
        yield app
        db.session.remove()
        db.drop_all()


@pytest.fixture
def client(app):
    return app.test_client()


def proximo(dia_semana, semanas=4):
    """Fecha del próximo `dia_semana` (0=lunes) a varias semanas en el futuro."""
    base = date.today() + timedelta(weeks=semanas)
    return base + timedelta(days=(dia_semana - base.weekday()) % 7)


class Api:
    def __init__(self, client):
        self.client = client
        self._n = 0

    def registrar(self, rol, nombre=None, **extra):
        self._n += 1
        datos = {"nombre": nombre or f"{rol} {self._n}", "email": f"{rol}{self._n}@prueba.co",
                 "password": "clave-segura", "rol": rol, "autoriza_datos": True}
        datos.update(extra)
        r = self.client.post("/auth/registro", json=datos)
        assert r.status_code == 201, r.get_json()
        cuerpo = r.get_json()
        return {"id": cuerpo["usuario"]["id"], "email": datos["email"],
                "h": {"Authorization": f"Bearer {cuerpo['token']}"}}

    def crear_clase(self, docente, inicio, duracion=60, estudiantes=(), materia="Matemáticas"):
        return self.client.post("/clases", headers=docente["h"], json={
            "materia": materia, "inicio": inicio, "duracion_min": duracion,
            "estudiantes": [e["id"] for e in estudiantes]})

    def avisos(self, usuario):
        return self.client.get("/notificaciones/me", headers=usuario["h"]).get_json()


@pytest.fixture
def api(client):
    return Api(client)
