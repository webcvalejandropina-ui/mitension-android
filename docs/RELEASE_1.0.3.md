# Mi Tensión Android 1.0.3 — beta de reloj

- Pantalla opcional «Reloj · Beta» dentro de «Cuida tu rutina» para consultar el pulso y la presión que Health Connect ya identifique como procedentes de un reloj Wear OS.
- Revisión y confirmación explícita antes de guardar una lectura de presión. Las lecturas manuales se conservan y siguen disponibles sin Health Connect.
- Prevención de duplicados al importar de nuevo la misma lectura. Permisos de **solo lectura**, solicitados al abrir la función; sin sincronización automática ni servidor propio.
- La pantalla de herramientas ahora se desplaza verticalmente para que la nueva opción no corte el contenido en móviles pequeños.

**Beta sin prueba con reloj real.** No calcula presión a partir del pulso. Samsung Health Monitor no garantiza que sus lecturas estén disponibles en Health Connect. La compatibilidad depende del modelo, la región, las aplicaciones del reloj, los permisos y los metadatos compartidos. La app no sustituye un tensiómetro validado ni la atención sanitaria. [Alcance y limitaciones](WEAR_BETA.md).

Compilación Debug/Release, doce tests unitarios y lint correctos; prueba instrumentada compilada, pero no ejecutada en dispositivo para esta versión. APK de prueba firmado con la clave Debug, Android 8 o posterior. No es una publicación en Google Play. Antes de actualizar, exporta una copia Excel del histórico.
