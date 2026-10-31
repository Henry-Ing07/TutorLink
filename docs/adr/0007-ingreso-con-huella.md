# ADR-0007: Ingreso con huella

**Contexto.** Los docentes coordinan de pie y entre clases (FPI-04). Escribir el correo y la
contraseña cada vez que se cierra la sesión es una fricción. Se pidió poder entrar con la huella
a la misma cuenta que ya se usa en el celular.

**Decisión.**
- Después de entrar con contraseña, la app ofrece activar la huella. También se puede activar o
  desactivar desde el ícono de huella de la barra superior. Activarla desde ahí pide la contraseña
  y la verifica con el servidor.
- El correo y la contraseña de **una** cuenta se guardan cifrados (AES-256-GCM) con una llave del
  Android Keystore creada con:
  - `setUserAuthenticationRequired(true)`: cada uso de la llave exige una huella reconocida (en
    Android 11 o superior, `setUserAuthenticationParameters(0, BIOMETRIC_STRONG)`).
  - `setInvalidatedByBiometricEnrollment(true)`: si alguien registra otra huella en el celular, la
    llave deja de servir, la app borra la cuenta guardada y pide la contraseña para activarla otra vez.
- Solo se aceptan biometrías de clase fuerte (`BIOMETRIC_STRONG`). El botón "Usar contraseña"
  vuelve al formulario normal.
- La huella se guarda aparte de la sesión, así sigue disponible después de cerrar sesión. Las reglas
  de respaldo excluyen esas preferencias de copias en la nube y transferencias entre equipos.
- Si el servidor rechaza la contraseña guardada (por ejemplo, porque se cambió), la huella se
  desactiva y se pide la contraseña nueva.
- Al abrir la app con la huella activada, el diálogo aparece solo. Después de cerrar sesión no
  aparece solo, por si se quiere entrar con otra cuenta; queda el botón "Ingresar con huella".

**Alternativa descartada.** Que el servidor emita un token de dispositivo de larga duración y
revocable. Es más robusto (no guarda la contraseña ni cifrada y permite cerrar sesión en otros
equipos), pero exige cambios en el backend. Queda como mejora si se publica la app.

**Dependencias.** `androidx.biometric:biometric:1.1.0` (última estable) y
`androidx.fragment:fragment-ktx:1.8.9`, ambas Apache 2.0. `MainActivity` pasa a ser
`FragmentActivity` porque `BiometricPrompt` lo exige.

**Verificación (emulador, Android 17).** Activar tras el login; contraseña guardada cifrada (no
aparece en el archivo); entrar con la huella registrada; rechazar un dedo no registrado; no abrir el
diálogo solo después de cerrar sesión; desactivar y volver a activar desde el menú (rechaza la
contraseña incorrecta); invalidación al registrar una segunda huella. No se probó el caso de
contraseña cambiada en el servidor.
