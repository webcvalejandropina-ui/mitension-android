# Desarrollo

## Requisitos

Android Studio compatible con AGP 8.13.2 o Android SDK/CLI; JDK 17, SDK 36 y Build Tools 35.0.0. Se incluye el wrapper Gradle 8.13 con checksum de distribución. Android mínimo: API 26.

Abre la carpeta raíz en Android Studio y selecciona JDK 17 para Gradle. Configura tu SDK en `local.properties`, excluido de Git. No requiere Node ni el proyecto iOS. No publiques rutas personales, claves, keystores ni registros médicos.

En macOS con JDK 17 de Homebrew puedes configurar la sesión:

```sh
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
```

En otros sistemas configura la ruta de tu JDK 17.

## Compilar y probar

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# Con un emulador o dispositivo conectado:
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.mitension/.MainActivity

# Genera un APK Release sin firma de distribución:
./gradlew :app:assembleRelease
```

El APK Debug es instalable para pruebas. El Release sin firma no lo es. Elegir identificador propio, clave privada de distribución y completar las comprobaciones pendientes antes de producción. Revisa INSTALLATION.md antes de cambiar firma o identificador, para no perder registros al actualizar.

## Estructura

- `app/src/main/java/com/example/mitension/model/`: lecturas, validación, agrupación y planificación de avisos.
- `data/`: almacenamiento atómico, Excel, PDF e impresión.
- `reminders/`: horarios, AlarmManager, receivers y servicio sonoro.
- `MainActivity.kt`, `Tools.kt`, `Texts.kt`: interfaz, navegación, ajustes e idiomas.
- `app/src/main/res/`: traducciones, imágenes y configuración de compartir.
- `app/src/test/`: tests JVM con datos sintéticos.
- `app/src/androidTest/`: tests de almacenamiento y navegación.
- `gradle/`: wrapper; `docs/`: documentación y medios públicos.

## Avisos

Guardar horarios conserva la configuración aunque se denieguen permisos. Android 13 o posterior exige permiso de notificaciones y Android 12 o posterior limita las alarmas exactas mediante permiso especial. Sin permiso de alarmas exactas, los avisos pueden retrasarse y no se inicia la alarma continua. Forzar la detención puede impedir avisos hasta reabrir la app.

Se planifican la próxima ocurrencia principal y el próximo segundo aviso por periodo. Cada entrega reconcilia las siguientes; arranque, cambios de hora/zona y reapertura también renuevan la planificación. La alarma tiene una acción Detener y límite de cinco minutos; depende del volumen y del fabricante.

## Referencias

- [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Alarmas de Android](https://developer.android.com/develop/background-work/services/alarms)
- [Medición domiciliaria, American Heart Association](https://www.heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/monitoring-your-blood-pressure-at-home)

Las referencias de presión son orientativas para adultos, no objetivos terapéuticos personales ni diagnóstico. No se aplican a menores ni al embarazo. Revisar accesibilidad, traducciones y contenido clínico antes de producción.
