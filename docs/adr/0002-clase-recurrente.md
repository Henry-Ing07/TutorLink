# ADR-0002: Clase recurrente como serie que genera ocurrencias

**Contexto.** RF-03 / HU-02: el docente piensa en franjas semanales, pero cada semana puede
moverse o cancelarse una sola ocurrencia. Opciones: (a) guardar solo la regla y calcular las
fechas al consultar; (b) guardar la serie y generar las clases individuales al crearla.

**Decisión.** Opción (b). La tabla `series` guarda la regla (día, hora, duración, período) y al
crearla se insertan todas sus `clases` con `serie_id`. La serie se valida completa contra la
agenda: si una sola fecha se cruza, no se crea ninguna ocurrencia y se informa la fecha.

**Consecuencias.**
- Reagendar o cancelar una ocurrencia es modificar una fila; las demás no cambian.
- "Esta y las siguientes" desplaza las ocurrencias activas posteriores de la misma serie.
- La validación de cruces (`app/horario.py`) siempre trabaja con clases concretas, la misma
  para crear clase, crear serie y reagendar (relación «include» del caso de uso).
- Se limita una serie a 52 semanas para acotar el número de filas generadas.
