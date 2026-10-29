# ADR-0004: Avisos mediante bandeja en el backend y consulta periódica desde la app

**Contexto.** HU-04 (Must Have, riesgo alto) exige avisar a estudiantes y acudientes. El
diagrama de contexto prevé un servicio externo de notificaciones push. Integrar Firebase Cloud
Messaging exige un proyecto de Firebase y credenciales que aún no existen, y el caso de uso
«Reagendar» establece que el cambio de la clase y la entrega del aviso no son atómicos.

**Decisión.**
- El backend crea un registro en `notificaciones` por cada destinatario (estudiante y, si es
  menor, su acudiente) y lo entrega a través de un *proveedor* intercambiable
  (`app/notificaciones.py`). El proveedor inicial, `bandeja`, deja el aviso disponible para
  que la app lo descargue.
- La app consulta `/notificaciones/me?despues_de=<último>` con WorkManager (cada 15 minutos con
  red, y al abrirla) y muestra cada aviso nuevo como notificación del sistema.
- Si un proveedor falla, la clase queda guardada, el aviso queda `pendiente` con su intento
  registrado, la respuesta informa `avisos_pendientes` y `flask reintentar-avisos` lo reintenta
  hasta `NOTIFICACION_MAX_INTENTOS` (flujo E2).
- No se notifica si una edición no cambió nada real (caso límite de HU-04).

**Consecuencias.** El aviso puede tardar hasta 15 minutos con la app cerrada. Agregar FCM es
registrar un nuevo proveedor en `_PROVEEDORES` sin cambiar las reglas de negocio.
