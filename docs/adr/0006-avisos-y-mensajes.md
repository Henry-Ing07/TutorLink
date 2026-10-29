# ADR-0006: Avisos del docente y mensajes privados (cambio de alcance)

**Contexto.** En la FPI-07 el chat por clase (HU-05) quedó como *Won't Have* porque WhatsApp
seguía siendo el canal de comunicación. Después, el responsable del proyecto pidió incluir:
(1) avisos que el docente escribe a sus estudiantes y (2) mensajes privados con estudiantes y
acudientes. Este cambio de alcance debe registrarse en la siguiente FPI, con su causa e impacto.

**Decisión.**
- **Avisos del docente** (`POST /anuncios`): se envían a una clase propia, a algunos estudiantes
  propios o a todos. Usan la misma bandeja y el mismo reintento de los avisos automáticos
  (ADR-0004), y también llegan al acudiente de cada menor.
- **Mensajes privados** (`/contactos`, `/mensajes/<id>`): son conversaciones uno a uno, no un chat
  por clase. Solo se permiten entre un docente y:
  - un estudiante **mayor de edad** inscrito en alguna de sus clases, o
  - el **acudiente** de un menor inscrito en alguna de sus clases.

  Con los menores, el docente se comunica a través del acudiente (Ley 1581 de 2012 y protección
  de menores). Entre estudiantes no hay mensajes. Cualquier otro intento responde 403 y queda en
  la bitácora de seguridad (ADR-0003).
- **Entrega:** cada mensaje genera un aviso de tipo `mensaje` para mostrarlo como notificación
  del sistema. Los avisos de mensajes no aparecen en la pestaña Avisos. Mientras la conversación
  está abierta, la app consulta los mensajes nuevos cada 4 segundos.
- **Acudiente:** gana una vista de solo lectura con las clases de sus menores
  (`GET /acudientes/me/clases`).

**Consecuencias.**
- La comunicación queda dentro del sistema, pero no reemplaza a WhatsApp para cobros y material
  (la frontera de la FPI-04 se mantiene).
- El chat no es en tiempo real: los mensajes nuevos se consultan periódicamente.
- Se almacenan conversaciones que pueden contener datos personales. Se aplica el mismo control de
  acceso y los mensajes no se escriben en los logs (DoD punto 8).
- Cubierto por `tests/test_comunicacion.py`.
