# Instalación y distribución

## APK de prueba

Descarga `Mi-Tension-Android-1.0.1-preview.apk` desde la sección Releases del repositorio. No requiere una cuenta ni conexión de la app a Internet. Requiere Android 8.0/API 26 o posterior.

Abre el APK en el teléfono y, si Android lo solicita, autoriza la instalación desde esa fuente concreta. Puedes retirar ese permiso después. No desactives las protecciones generales del teléfono. También puedes instalar con `adb install -r Mi-Tension-Android-1.0.1-preview.apk` desde un ordenador con Android SDK y depuración autorizada.

El archivo `SHA256SUMS.txt` permite comprobar la integridad del APK descargado. En macOS/Linux utiliza `shasum -a 256 Mi-Tension-Android-1.0.1-preview.apk` y compara el resultado. Esto detecta alteraciones del archivo; no sustituye una revisión de seguridad.

## Uso inicial

1. Abre Mi Tensión y registra una o tres lecturas obtenidas con un tensiómetro externo. Puedes añadir pulso, notas y medicamentos.
2. Comprueba el histórico: las lecturas se agrupan por día y mañana/noche usando la zona horaria del teléfono. Antes de las 14:00 es mañana; desde las 14:00 es noche.
3. Cierra y vuelve a abrir la app para comprobar que conserva tus registros. Las lecturas guardadas solo se pueden eliminar, no editar.
4. En las herramientas puedes exportar/importar Excel, consultar la guía y configurar avisos. Los permisos de avisos y alarmas se solicitan por separado; sin ellos Android limita estas funciones.
5. En Médico puedes compartir un PDF o imprimir. Revisa el archivo antes de compartirlo: contiene información de salud.

La app guarda datos en el dispositivo, no en servidores propios. No significa que no guarde ningún dato. No hay copia automática en la nube; conserva una exportación Excel si necesitas respaldo.

## Firma y futuras actualizaciones

Esta versión utiliza la firma de desarrollo Debug, que no debe usarse para producción. El identificador actual es `com.example.mitension`. El APK Release generado por el proyecto no tiene firma de distribución y no se instala directamente.

Para distribuir una versión definitiva hay que elegir un identificador propio, preparar una clave privada de publicación y conservarla fuera del repositorio. Actualizar una instalación existente requiere un identificador y una firma compatibles, además de un código de versión superior. Cambiar de firma o identificador puede exigir otra instalación: exporta primero tus registros; desinstalar elimina los datos locales.

No se incluyen claves privadas, históricos reales, exportaciones médicas ni configuraciones personales del SDK en GitHub. Los datos de los tests son sintéticos.

## Condiciones antes de producción

Completar las pruebas pendientes de teléfono, permisos, accesibilidad, idiomas, importación y actualización indicadas en VERIFICATION.md. Para Google Play también se necesitan la cuenta de desarrollador, el responsable y contacto de privacidad, las declaraciones de datos y salud, la firma de publicación y revisión de los requisitos aplicables. Esta entrega no realiza ese trámite.
