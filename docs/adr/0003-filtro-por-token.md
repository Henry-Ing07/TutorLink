# ADR-0003: Filtrar los datos por el usuario del token, no por parámetros

**Contexto.** HU-03 exige que un estudiante vea solo sus clases y reciba 403 al pedir una ajena.
Si el cliente enviara su propio identificador (`?estudiante_id=…`), bastaría cambiarlo para
leer datos de otros, incluidos menores de edad.

**Decisión.** Las rutas "propias" usan `me` (`/estudiantes/me/clases`, `/docentes/me/clases`,
`/notificaciones/me`) y el usuario se obtiene exclusivamente del JWT firmado. Cualquier acceso
a un recurso que no pertenece al usuario responde 403 con cuerpo vacío (también si el recurso
no existe, para no revelar qué identificadores existen) y se registra en `bitacora_seguridad`.

**Consecuencias.** Las pruebas `test_hu03_mis_clases.py` verifican el aislamiento, el 403 sin
datos y que los parámetros enviados por el cliente se ignoran.
