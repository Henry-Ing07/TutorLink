# TutorLink

Aplicación móvil para la gestión de asesorías académicas entre docentes particulares y
estudiantes (Proyecto Integrador, Ingeniería de Software I — Universidad de Pamplona).

El docente crea clases (materia, fecha, hora, duración) y agrega a sus estudiantes; el sistema
rechaza cualquier cruce de horario; cada estudiante ve solo sus clases; y estudiantes y
acudientes de menores reciben un aviso cuando una clase se crea, cambia o se cancela.

## Alcance del MVP (FPI-07)

| Historia | MoSCoW | Estado |
|---|---|---|
| HU-01 Crear clase con estudiantes y rechazo de cruces (RF-01, RF-02) | Must | Implementada |
| HU-03 El estudiante ve solo sus clases; 403 a clases ajenas (RF-04, RNF-03) | Must | Implementada |
| HU-04 Avisos a estudiante y acudiente del menor (RF-05) | Must | Implementada |
| HU-02 Franja recurrente semanal; editar/cancelar una ocurrencia (RF-03) | Should | Implementada |
| HU-06 Historial de asesorías | Could | No incluida |
| HU-05 Chat por clase | Won't (esta vez) | No incluida: WhatsApp sigue siendo el canal |

Fuera de la frontera del sistema (FPI-04): pagos, videollamadas, material de estudio,
calificaciones y captación de alumnos.

## Estructura

```
backend/   API REST en Flask + SQLAlchemy (SQLite en desarrollo, MySQL en producción)
android/   App Android nativa en Kotlin + Jetpack Compose
docs/      Definition of Done, ADRs y contrato de la API
```

Arquitectura: monolito por capas con una base de datos relacional y un backend único
(FPI-03: se rechazó la propuesta de microservicios por sobrediseño).

## Ejecutar el backend

Requisitos: Python 3.11+.

```bash
cd backend
python -m venv .venv
.venv/Scripts/activate        # Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env          # y cambiar SECRET_KEY por una clave aleatoria de 32+ caracteres
flask --app wsgi init-db
flask --app wsgi seed         # datos ficticios de demostración
python wsgi.py                # http://localhost:5000
```

Usuarios de demostración (todos con la contraseña `tutorlink123`):
`docente@demo.co`, `ana@demo.co` (estudiante), `tomas@demo.co` (estudiante menor),
`acudiente@demo.co` (acudiente de Tomás).

Pruebas y estilo (DoD puntos 2 y 3):

```bash
python -m pytest
python -m flake8
```

Avisos que el proveedor no confirmó (flujo E2) se reintentan con
`flask --app wsgi reintentar-avisos`, que se programa cada pocos minutos en el servidor.

Para MySQL: `DATABASE_URL=mysql+pymysql://usuario:clave@host:3306/tutorlink?charset=utf8mb4`.

## Ejecutar la app Android

Requisitos: Android Studio (con su JDK incluido) y el SDK de Android 37.

1. Abrir la carpeta `android/` en Android Studio, o compilar por consola:
   `./gradlew assembleDebug` (el APK queda en `android/app/build/outputs/apk/debug/`).
2. Con el backend corriendo en el computador, redirigir el puerto al emulador o teléfono:
   `adb reverse tcp:5000 tcp:5000`. La compilación *debug* usa `http://127.0.0.1:5000`; la
   *release* exige HTTPS. (No se usa `10.0.2.2` porque Android 17 bloquea por defecto el
   acceso de las apps a la red local.)

## Seguridad y datos personales

- Contraseñas con bcrypt de costo 12; sesiones con JWT firmado (HS256).
- Toda consulta se filtra por el usuario del token, nunca por un parámetro (ADR-0003).
- El acceso a un recurso ajeno responde 403 sin cuerpo y queda en la bitácora de seguridad.
- Del menor solo se guardan nombre, correo y el vínculo con su acudiente, que se registra
  primero y autoriza el tratamiento de datos (Ley 1581 de 2012).
- El estudiante no ve los datos de sus compañeros de clase.
- La app solo permite tráfico sin cifrar hacia el backend local del emulador.
