from datetime import datetime, timedelta

import click
from flask import Flask, jsonify

from .config import Config
from .extensions import db


def create_app(config=None):
    app = Flask(__name__)
    app.config.from_object(Config)
    if config:
        app.config.update(config)
    if len(app.config.get("SECRET_KEY") or "") < 32:
        raise RuntimeError("SECRET_KEY debe tener al menos 32 caracteres (ver .env.example)")

    db.init_app(app)

    from . import api_auth, api_avisos, api_clases
    from .seguridad import registrar_manejadores
    app.register_blueprint(api_auth.bp)
    app.register_blueprint(api_clases.bp)
    app.register_blueprint(api_avisos.bp)
    registrar_manejadores(app)

    @app.get("/salud")
    def salud():
        return jsonify({"estado": "ok"})

    _registrar_comandos(app)
    return app


def _registrar_comandos(app):
    @app.cli.command("init-db")
    def init_db():
        """Crea las tablas."""
        from . import models  # noqa: F401
        db.create_all()
        click.echo("Tablas creadas.")

    @app.cli.command("reintentar-avisos")
    def reintentar_avisos():
        """Reintenta los avisos pendientes (programar cada pocos minutos)."""
        from .notificaciones import reintentar_pendientes
        total, fallidos = reintentar_pendientes()
        click.echo(f"Reintentados: {total}. Siguen pendientes: {fallidos}.")

    @app.cli.command("seed")
    def seed():
        """Carga datos ficticios de demostración (nunca datos reales)."""
        from .models import Clase, Usuario
        from .seguridad import ahora, hashear_password

        if Usuario.query.first():
            click.echo("La base ya tiene datos; no se cargó nada.")
            return
        clave = hashear_password("tutorlink123")
        marca = datetime.utcnow()

        def usuario(nombre, email, rol, **extra):
            u = Usuario(nombre=nombre, email=email, rol=rol, password_hash=clave,
                        autoriza_datos_en=marca, **extra)
            db.session.add(u)
            db.session.flush()
            return u

        docente = usuario("Laura Méndez", "docente@demo.co", "docente")
        acudiente = usuario("Marta Ríos", "acudiente@demo.co", "acudiente")
        ana = usuario("Ana Gómez", "ana@demo.co", "estudiante")
        tomas = usuario("Tomás Ríos", "tomas@demo.co", "estudiante", es_menor=True,
                        acudiente_id=acudiente.id)
        manana = ahora().replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=1)
        for dias, hora, materia, duracion, alumnos in [
            (0, 14, "Matemáticas", 60, [ana, tomas]),
            (0, 16, "Física", 90, [tomas]),
            (1, 10, "Inglés", 60, [ana]),
            (3, 15, "Programación", 120, [ana]),
        ]:
            clase = Clase(docente_id=docente.id, materia=materia, estudiantes=alumnos)
            clase.programar(manana + timedelta(days=dias, hours=hora), duracion)
            db.session.add(clase)
        db.session.commit()
        click.echo("Datos de demostración cargados. Contraseña de todos: tutorlink123")
