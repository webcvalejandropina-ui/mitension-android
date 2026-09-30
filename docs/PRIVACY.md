# Privacidad de la versión Android

Las tomas, notas y medicamentos se guardan en el sandbox privado del móvil. No existe cuenta, servidor propio, analítica ni permiso de Internet. Compartir archivos y abrir fuentes médicas externas son acciones explícitas del usuario.

Persistencia interna JSON con AtomicFile: valida todo antes de escribir, conserva el archivo anterior si falla y bloquea escrituras si el original está corrupto. Las preferencias guardan horarios y el cierre del aviso, no mediciones. Los datos residen en almacenamiento protegido por el sistema; no se afirma cifrado personalizado.

Esta versión desactiva la copia automática de Android (`allowBackup=false`). Exportar Excel si se desea conservar registros; borrar la app puede borrar el histórico. Algunos fabricantes pueden tener comportamientos propios de transferencia que requieren verificación real.

FileProvider solo expone archivos de `cache/exports` y concede acceso temporal al destinatario elegido. Los archivos incluyen salud y no deben subirse a GitHub. Notificaciones con visibilidad privada; la visualización final en pantalla bloqueada depende de Ajustes.

Permisos básicos: notificaciones, alarmas exactas, arranque y servicio visible de reproducción para la alarma opcional. La beta Wear OS declara permisos de **lectura** de pulso y presión de Health Connect, que se solicitan solo al pulsar «Conectar y buscar datos». Sin cámara, micrófono, ubicación, contactos ni pagos. No se habilitan permisos en el teléfono del usuario durante las pruebas automatizadas.

Antes de publicar en Google Play: completar identidad/contacto públicos y revisar las declaraciones de Datos/Salud/servicio, incluidas las lecturas de Health Connect. Este documento técnico no sustituye una política legal final ni una auditoría de seguridad.

# Beta Wear OS (versión 1.0.3 de prueba)

Si el usuario abre la pantalla beta y concede permisos a Health Connect, la app puede leer pulso y presión arterial identificados como procedentes de un reloj. No se activa en segundo plano. Solo una medición de presión confirmada por el usuario pasa al diario local. La entrada manual sigue funcionando sin esos permisos. No se envían estos datos a servidores propios. Más detalles en [WEAR_BETA.md](WEAR_BETA.md).
