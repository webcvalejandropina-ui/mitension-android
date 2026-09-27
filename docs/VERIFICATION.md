# Verificación y límites

Versión 1.0.2: compilación Debug, once tests JVM y lint sin errores tras ajustar la tipografía y los colores de las tomas. Revisión visual del resumen e histórico con tres lecturas sintéticas en claro y oscuro en el emulador Android TV API 34 ajustado a proporciones de teléfono. No se ha repetido en esta versión la batería instrumentada de la 1.0.1 ni se ha probado en teléfono físico. Ningún dato del usuario ni su captura se publica.

Actualización de diseño 1.0.1: compilación Debug y lint sin errores; once tests JVM y seis tests instrumentados completados sin fallos. Se verifican los filtros con el mismo corte temporal que iPhone (incluido cambio de hora), métricas del resumen, navegación inferior y filtros médicos, además de las regresiones de almacenamiento y Excel existentes. Capturas del resumen claro/oscuro revisadas en el emulador Android TV API 34 con resolución 1080×2400 y densidad 420. No hay datos reales en las capturas. No se ha realizado una comparación píxel a píxel ni una prueba en teléfono real. Ver DESIGN_PARITY.md.

Proyecto independiente: ninguna fuente ni configuración iOS se modifica para crear Android.

Primera comprobación: APK Debug generado y nueve tests JVM sin fallos (Unicode, valores, periodo local, agrupación, Excel 1/2/3 de cada periodo, medicamentos/fecha exacta, fórmulas/UUID conflictivos, XML externo y planificación de segundos avisos). Las fixtures son sintéticas. Las posteriores correcciones de seguridad requieren repetir esa batería.

Compilaciones Debug y Release sin firma de distribución correctas; lint sin errores (con advertencias, entre ellas versiones más recientes de dependencias). La batería ampliada ejecuta diez tests JVM sin fallos, incluida la identidad UUID con mayúsculas/minúsculas al intercambiar Excel con iOS. Cinco tests instrumentados completados sin fallos en Android TV API 34: persistencia y eliminación, deduplicación/rechazo atómico, archivo corrupto y dos pruebas de navegación. No se concedieron permisos de notificaciones ni alarmas.

La descarga de imágenes de emulador de teléfono Android 14 y Android 11 falló por espacio insuficiente. Se aprovecha el Android TV API 34 ya instalado, en modo de solo lectura y sin sonido, para comprobar ejecución y tests instrumentados. Esto no verifica diseño, teclado o avisos en un teléfono real. No conceder permisos ni lanzar alarmas sonoras durante estos tests.

Verificación final del 27/09/2026: recompilación Debug/Release y diez tests JVM correctos después de fijar fechas ISO con milisegundos explícitos para compatibilidad iOS. Cinco tests instrumentados correctos en la última ronda de navegación/almacén. Manifest del APK comprobado: mínimo API 26, destino API 36, sin permiso de Internet. Se cerró el emulador de pruebas al terminar; no se guardaron snapshots ni se modificó el repositorio iOS.

Pendiente antes de producción: dispositivo/emulador de teléfono, modos claro/oscuro, árabe/RTL, texto grande y TalkBack, actualización sin pérdida de datos, teclado real, todos los proveedores de Archivos, PDF/impresoras, notificaciones en segundo plano/reinicio/Doze/Concentración y sonido de alarma. No hay cuenta Google Play configurada ni firma de publicación. La app no se ha publicado ni enviado a revisión.
