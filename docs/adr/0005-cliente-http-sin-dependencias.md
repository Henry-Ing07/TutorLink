# ADR-0005: Cliente HTTP de la app sin librerías externas

**Contexto.** El acuerdo ético de IA (FPI-01) obliga a verificar y registrar la licencia de cada
librería. La app hace pocas llamadas JSON a un único backend.

**Decisión.** Usar `HttpURLConnection` y `org.json`, incluidos en Android, en lugar de
Retrofit/OkHttp/Gson. La última respuesta del horario se guarda en `SharedPreferences` privadas
para mostrarla sin conexión (RNF-02).

**Dependencias de la app y licencias.** Todas Apache 2.0: AndroidX Core, Activity, Lifecycle,
WorkManager y Jetpack Compose (Material 3).

**Consecuencias.** Menos código de terceros que auditar. Si la API crece mucho, se reevaluará
Retrofit (Apache 2.0).
