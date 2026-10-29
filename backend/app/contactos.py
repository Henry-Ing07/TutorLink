"""Quién puede escribirle a quién (ADR-0006).

Solo hay conversaciones entre un docente y:
- un estudiante mayor de edad inscrito en alguna de sus clases, o
- el acudiente de un estudiante menor inscrito en alguna de sus clases.

Con los menores el docente se comunica a través del acudiente (Ley 1581 de 2012 y
protección de menores); entre estudiantes no hay mensajes.
"""
from .models import Clase, Usuario, clase_estudiante


def estudiantes_del_docente(docente_id):
    return (Usuario.query
            .join(clase_estudiante, clase_estudiante.c.estudiante_id == Usuario.id)
            .join(Clase, Clase.id == clase_estudiante.c.clase_id)
            .filter(Clase.docente_id == docente_id)
            .distinct().all())


def _docentes_de(estudiante_ids):
    if not estudiante_ids:
        return []
    return (Usuario.query
            .join(Clase, Clase.docente_id == Usuario.id)
            .join(clase_estudiante, clase_estudiante.c.clase_id == Clase.id)
            .filter(clase_estudiante.c.estudiante_id.in_(estudiante_ids))
            .distinct().all())


def contactos_de(usuario):
    """Devuelve {id_contacto: (Usuario, descripción de la relación)}."""
    contactos = {}
    if usuario.rol == "docente":
        for estudiante in estudiantes_del_docente(usuario.id):
            if not estudiante.es_menor:
                contactos[estudiante.id] = (estudiante, "Estudiante")
            elif estudiante.acudiente is not None:
                acudiente = estudiante.acudiente
                anterior = contactos.get(acudiente.id)
                menores = (anterior[1].removeprefix("Acudiente de ") + ", " if anterior else "")
                contactos[acudiente.id] = (acudiente, f"Acudiente de {menores}{estudiante.nombre}")
    elif usuario.rol == "estudiante" and not usuario.es_menor:
        for docente in _docentes_de([usuario.id]):
            contactos[docente.id] = (docente, "Docente")
    elif usuario.rol == "acudiente":
        menores = Usuario.query.filter_by(acudiente_id=usuario.id).all()
        for docente in _docentes_de([m.id for m in menores]):
            contactos[docente.id] = (docente, "Docente")
    return contactos


def puede_conversar(usuario, otro_id):
    return otro_id in contactos_de(usuario)
