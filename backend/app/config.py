import os

from dotenv import load_dotenv

# Se carga antes de leer las variables: la clase Config las toma al importarse.
load_dotenv()


class Config:
    SECRET_KEY = os.environ.get("SECRET_KEY")
    SQLALCHEMY_DATABASE_URI = os.environ.get("DATABASE_URL", "sqlite:///tutorlink.db")
    SQLALCHEMY_ENGINE_OPTIONS = {"pool_pre_ping": True}
    BCRYPT_ROUNDS = int(os.environ.get("BCRYPT_ROUNDS", "12"))
    TOKEN_HORAS = int(os.environ.get("TOKEN_HORAS", "168"))
    UTC_OFFSET_HORAS = int(os.environ.get("UTC_OFFSET_HORAS", "-5"))
    NOTIFICADOR = os.environ.get("NOTIFICADOR", "bandeja")
    NOTIFICACION_MAX_INTENTOS = int(os.environ.get("NOTIFICACION_MAX_INTENTOS", "5"))
    JSON_SORT_KEYS = False
