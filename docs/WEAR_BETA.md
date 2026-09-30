# Wear OS y Galaxy Watch — beta 1.0.3

Esta pantalla es una **prueba de conectividad**. Se abre desde «Cuida tu rutina» y no sustituye el registro manual.

## Qué hace

- Pide consentimiento de lectura de pulso y presión arterial en Health Connect solo al pulsar «Conectar y buscar datos».
- Muestra el último pulso que Health Connect identifique como procedente de un reloj durante el último día. Es una lectura del sensor, **no una estimación de la tensión**.
- Muestra mediciones de presión de los últimos siete días **solo si ya existen** en Health Connect y su metadato de dispositivo indica `TYPE_WATCH`. Nunca calcula presión a partir del pulso, PPG u otros sensores.
- Una medición se añade al diario únicamente tras revisar y confirmar sus cifras. El ID estable de Health Connect evita duplicados; no modifica ni elimina las tomas introducidas manualmente.
- Si Health Connect no está disponible, no se conceden permisos o la aplicación del reloj no comparte registros marcados como «watch», la pantalla explica la limitación y el usuario puede continuar con la entrada manual.

Los datos se leen al pedirlos, sin sincronización automática, servicios en segundo plano ni servidores propios. La app conserva la lectura importada en su almacén local como cualquier otra toma. La procedencia queda en una nota dentro de la lectura y también aparecerá en las exportaciones. La app no puede verificar la calibración del reloj: revisa la lectura en el sistema original antes de guardarla.

## Limitaciones conocidas

Samsung Health Monitor permite medir presión con modelos y regiones compatibles tras calibrar el reloj con un tensiómetro de brazo; Samsung indica recalibración cada 28 días. **Eso no significa que esa lectura aparezca en Health Connect ni que esta app pueda acceder directamente a Samsung Health Monitor.** Si no aparece como dato procedente de reloj, no se ofrece importación.

El SDK directo de Samsung Health exige aprobación y registro de identificador y firma para distribuir apps; además, el emulador no es compatible con su SDK de sensores. Esta beta usa la API estándar Health Connect. Antes de anunciar compatibilidad específica con Galaxy Watch se requieren pruebas en un reloj real y comprobar qué aplicaciones comparten los datos, permisos, duplicados y exactitud de procedencia. No hay reloj disponible para las pruebas actuales.

Esta función está restringida a **registros cuyo metadato indica reloj**. No activa sensores del teléfono ni importa registros de pulso o presión de otra procedencia. El proveedor puede omitir o etiquetar incorrectamente el metadato; en ese caso no se presenta como lectura de reloj. El pulso mostrado puede no coincidir temporalmente con una presión mostrada y no se asocia automáticamente a ella.

Fuentes: [Health Connect y permisos](https://developer.android.com/health-and-fitness/health-connect/get-started), [metadatos de dispositivo](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Device), [Samsung Health Monitor](https://www.samsung.com/us/apps/samsung-health-monitor/), [restricciones de distribución del SDK Samsung](https://developer.samsung.com/health/data/guide/app-verification.html).
