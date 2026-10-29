# Definition of Done — TutorLink (FPI-03)

Una tarea solo pasa a "Terminado" si cumple todos los criterios:

1. **Código**: compila y ejecuta sin errores desde un clon limpio siguiendo únicamente el README.
2. **Estilo**: pasa el analizador estático sin advertencias (Android Lint en la app; flake8 en
   el backend), sin código comentado ni variables sin uso.
3. **Pruebas**: al menos una prueba automatizada por criterio de aceptación, con al menos un
   caso límite o de error, y todas pasan.
4. **Criterios de aceptación**: cada criterio verificado y marcado en la tarjeta del tablero.
5. **Revisión**: revisión diferida (24 h después) con lista de chequeo: legibilidad, manejo de
   errores, validación de entradas, ausencia de duplicación; registrada en el pull request.
6. **Integración**: integrado a la rama principal por pull request, rama de trabajo eliminada.
7. **Secretos**: ninguna credencial en el código ni en el historial; `.env` en `.gitignore` y
   `.env.example` documentado.
8. **Datos personales**: no se registran datos innecesarios de estudiantes ni se imprimen en logs.
9. **Documentación**: toda decisión técnica no obvia queda en un ADR en `docs/adr/`.
10. **Trazabilidad**: la tarjeta enlaza al commit o PR y al requisito que la origina.
11. **RNF (FPI-07)**: la prueba de control de acceso y el comportamiento sin conexión de la
    historia están verificados.
