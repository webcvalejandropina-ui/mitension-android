# Mi Tensión Android 1.0.0 — versión de prueba

Aplicación nativa independiente en Kotlin y Jetpack Compose. Registro local de una o tres tomas, medicamentos, histórico por día/periodo, gráficas, vista médica con PDF e impresión, guía ilustrada, diez idiomas, Excel de importación/exportación y configuración de avisos con repetición a los 30 minutos si falta la toma.

## Descarga

Instala `Mi-Tension-Android-1.0.0-preview.apk` en Android 8 o posterior. El APK está firmado para pruebas (Debug). `SHA256SUMS.txt` contiene su checksum. Código fuente y dependencias de compilación disponibles en este repositorio; instrucciones en README y docs/INSTALLATION.md.

## Verificación

Compilación Debug y Release sin firma de distribución correctas; diez tests JVM y cinco tests instrumentados sin fallos. Los tests instrumentados se ejecutaron en el emulador Android TV API 34 disponible, no en un teléfono. Lint sin errores. Firma del APK Debug verificada.

Pendientes las pruebas de interfaz y teclado en teléfono, accesibilidad/RTL y notificaciones/alarma en condiciones reales. No se ha publicado en Google Play ni se presenta como producción certificada. Consulta docs/VERIFICATION.md.

No hay login, pagos, servidores propios ni permiso de Internet. Los registros permanecen en el dispositivo; exportarlos comparte información de salud. La app no mide la presión: registra lecturas de un tensiómetro externo. Sus referencias son orientativas y no sustituyen atención médica.
