# ADR-0001: Monolito por capas con base de datos relacional

**Contexto.** El MVP tiene cuatro historias y un único desarrollador. Una IA propuso
microservicios, Firestore, cola de mensajes y Kubernetes (auditoría FPI-03).

**Decisión.** Un solo backend Flask con SQLAlchemy sobre una base relacional (SQLite en
desarrollo, MySQL en producción) y una app Android nativa que consume su API REST.

**Consecuencias.** Despliegue simple en capa gratuita (restricción de presupuesto, FPI-05).
Si la carga lo justifica en el futuro, se evaluará separar componentes con datos medidos.
