# ADR-0008: La sesión se cierra al cerrar la app

**Contexto.** Por seguridad se pidió que, al cerrar la app, también se cierre la sesión: quien tome
el celular después no debe encontrar la cuenta abierta. Pero los avisos de clases (HU-04, Must
Have) se descargan en segundo plano con la app cerrada, y para eso el celular necesita alguna
credencial.

**Decisión.**
- **Dos tokens.** Al iniciar sesión, el servidor entrega:
  - `token` (alcance `sesion`): permite todo lo que el rol permite. La app lo guarda **solo en
    memoria**.
  - `token_avisos` (alcance `avisos`, 30 días, `TOKEN_AVISOS_DIAS`): **solo** sirve para
    `GET /notificaciones/me`. Cualquier otra ruta responde 403 sin cuerpo y deja registro en la
    bitácora. La app lo guarda en el almacenamiento privado para el trabajo de avisos en segundo
    plano.
- **Qué cuenta como cerrar la app:** salir con Atrás o quitarla de las apps recientes. En ambos
  casos se pierde la memoria de la app y, al volver, pide entrar otra vez (con la huella, si está
  activada; ADR-0007). Cambiar de app o apagar la pantalla no cierra la sesión.
- **Al abrir la app** se borran los horarios guardados de la sesión anterior y lo que guardaban
  versiones anteriores (token de sesión y usuario en disco).
- **Cerrar sesión con el botón** borra también el token de avisos y detiene los avisos en ese
  celular.
- **Notificación tocada con la app cerrada:** después de entrar se abre la pestaña de Mensajes o
  Avisos que correspondía.
- Un 401 al iniciar sesión (contraseña equivocada) ya no borra nada guardado. Antes borraba toda
  la sesión, incluso al confirmar la contraseña para activar la huella.
- Los tokens emitidos antes de este cambio no traen alcance y se tratan como de sesión. Un alcance
  desconocido se rechaza con 401.

**Consecuencias.**
- Si roban el almacenamiento de la app, el token de avisos solo deja leer la bandeja de avisos,
  no clases, mensajes ni cambios.
- Los tokens son JWT sin estado: el servidor no puede revocar uno antes de que venza. Para revocar
  hay que cambiar `SECRET_KEY`, lo que invalida todos. Si se publica la app, conviene guardar los
  tokens de avisos en la base de datos para revocarlos por dispositivo.
- Android "forzar detención" cancela los trabajos programados hasta que la app se vuelva a abrir.
  Es comportamiento del sistema; cerrar desde recientes no lo hace en un Android estándar, pero
  algunos fabricantes (Xiaomi/POCO con ahorro de batería) sí pueden hacerlo.

**Pruebas.** `tests/test_seguridad.py`: el login y el registro entregan el token de avisos; ese
token descarga la bandeja; es rechazado (403 y bitácora) en 7 rutas, incluida la creación de
clases; los tokens viejos siguen funcionando y un alcance desconocido se rechaza. En el emulador:
contraseña equivocada no borra nada, en el disco no queda el token de sesión, Inicio conserva la
sesión, y Atrás o quitarla de recientes la cierran.
