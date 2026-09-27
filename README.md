# Mi Tensión — Android

Proyecto nativo **independiente** de la app iOS y de la web. Kotlin + Jetpack Compose. No modifica ni necesita los proyectos anteriores para compilar. Las ilustraciones se copiaron como recursos propios de este proyecto.

La app registra lecturas de un tensiómetro externo: **el teléfono no mide la presión arterial**. Sin login, pagos, servidores, publicidad ni permiso de Internet.

## Descargar e instalar

[Repositorio Android](https://github.com/webcvalejandropina-ui/mitension-android) · [APK y versión de prueba](https://github.com/webcvalejandropina-ui/mitension-android/releases/tag/v1.0.0-preview)

El APK publicado es instalable en Android 8 o posterior y está firmado para pruebas (Debug). No es una versión certificada para producción ni una publicación en Google Play. Consulta [instalación, firma y actualización](docs/INSTALLATION.md) y [pruebas realizadas y pendientes](docs/VERIFICATION.md).

## Funciones

- Formulario de una o tres tomas, con UUID individual, pulso, notas y medicamentos por toma.
- Cada toma se guarda en el móvil; el lote se valida antes de una escritura atómica. Las tomas guardadas no se editan, solo se eliminan con confirmación.
- Menú horizontal con Resumen, Histórico, Gráficas y Médico; agrupación por fecha local y mañana/noche (corte a las 14:00).
- Gráfica de las últimas 60 tomas, vista médica, PDF compartible e impresión nativa.
- Exportación/importación `.xlsx`, compatible con el formato iOS: cada fila es una toma, admite 1/2/3 por periodo o más, deduplica UUID y rechaza archivos inválidos completos.
- Guía ilustrada ampliable, referencias orientativas y privacidad. Aviso de almacenamiento cerrable permanentemente.
- Horarios semanales guardados localmente, segundo aviso a los 30 minutos si falta la toma del día/periodo, alarma sonora opcional con acción Detener y límite de cinco minutos.
- Colores claros/oscuros y recursos para español, inglés, francés, alemán, italiano, portugués, catalán, chino simplificado, japonés y árabe según el dispositivo.

## Requisitos

- Android Studio compatible con AGP 8.13.2, o SDK/CLI Android.
- JDK 17 para Gradle; wrapper **Gradle 8.13** con checksum de distribución.
- SDK Android 36 y Build Tools 35.0.0.
- Android 8.0/API 26 o posterior en el dispositivo.

Abre **esta carpeta** en Android Studio, configura el SDK y Gradle JDK 17 y ejecuta `app`. `local.properties` contiene la ubicación del SDK de cada desarrollador y está excluido de Git. No requiere Node ni dependencias iOS.

En este Mac, el JDK 17 está instalado mediante Homebrew pero el lanzador Java del sistema no lo detecta automáticamente. Para ejecutar los comandos desde Terminal, configura antes la sesión (no cambia la configuración de iOS):

```sh
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
```

En otras máquinas, utilizar la ruta de su propio JDK 17. No guardar rutas personales en `gradle.properties` compartido.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease

# Con un dispositivo/emulador Android conectado:
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.mitension/.MainActivity
```

`assembleRelease` genera un APK **sin firma de distribución**. No es un lanzamiento aprobado para Google Play. El application ID es de ejemplo; elegir uno propio y firma privada antes de publicar. Nunca incluir keystores, claves, exportaciones médicas ni históricos en Git.

## Carpetas y documentación

- `app/src/main/java/.../model/`: validación, agrupación y planificación pura de avisos.
- `.../data/`: almacén atómico, ZIP/XML Excel, PDF y adaptador de impresión.
- `.../reminders/`: AlarmManager, receivers, configuración y servicio sonoro.
- `MainActivity.kt`, `Tools.kt`, `Texts.kt`: navegación, formularios, guía, idiomas y ajustes.
- `app/src/main/res/`: diez idiomas, estilos, imágenes y rutas restringidas de compartir.
- `app/src/test/`: regresiones JVM con datos sintéticos.
- `app/src/androidTest/`: almacenamiento aislado y navegación Compose, sin conceder permisos de avisos.
- `gradle/`: wrapper reproducible; `docs/`: [dependencias](docs/DEPENDENCIES.md), [formato Excel](docs/EXCEL.md), [privacidad](docs/PRIVACY.md) y [verificación](docs/VERIFICATION.md).

## Avisos y límites de Android

Los permisos no se conceden automáticamente. Guardar horarios conserva la configuración aunque se deniegue el permiso. Desde Android 13 se solicita permiso de notificaciones al guardar horarios activados. Desde Android 12, el permiso especial de alarmas exactas se concede desde Ajustes; sin él, los avisos pueden retrasarse y no se activa sonido continuo de alarma.

La app planifica la próxima ocurrencia y el próximo segundo aviso, no un horizonte de 21 días. Cada entrega renueva las siguientes; arranque, cambios de hora/zona y reapertura también reconcilian la planificación. Forzar detención puede impedir avisos hasta reabrir. La alarma usa un servicio visible con acción Detener; depende del volumen de alarmas y la configuración del fabricante. No detecta emergencias ni mediciones elevadas para activar alarmas.

## Fuentes técnicas y sanitarias

- [Compatibilidad oficial de AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Alarmas y permisos de Android](https://developer.android.com/develop/background-work/services/alarms)
- [Medición domiciliaria, American Heart Association](https://www.heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/monitoring-your-blood-pressure-at-home)

Las referencias de presión son orientativas para adultos, no diagnóstico ni objetivos terapéuticos personales. No se aplican al embarazo ni a menores. Se recomienda revisión clínica, lingüística, accesibilidad y pruebas reales de teléfono antes de producción.
