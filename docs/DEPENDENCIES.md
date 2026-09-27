# Dependencias propias de Android

| Dependencia | Versión | Uso |
|---|---|---|
| Gradle wrapper | 8.13 | Compilación reproducible, distribución con SHA-256. |
| Android Gradle Plugin | 8.13.2 | SDK 36 y empaquetado Android. |
| Kotlin / Compose compiler / serialization plugin | 2.2.0 | Lenguaje, Compose y modelos serializados. |
| Compose BOM | 2025.04.01 | Alinea Compose UI, Material 3 y pruebas. |
| Activity Compose | 1.10.1 | Activity, permisos y selector de Archivos. |
| Lifecycle Compose | 2.9.0 | Observación del histórico según ciclo de vida. |
| AndroidX Core | 1.16.0 | Notificaciones, FileProvider y compatibilidad. |
| Kotlin serialization JSON | 1.8.1 | Persistencia interna y metadatos dentro de XLSX. |
| JUnit | 4.13.2 | Regresiones de lógica pura. |
| AndroidX Test runner / JUnit extension | 1.6.2 / 1.2.1 | Pruebas instrumentadas aisladas. |

Excel usa ZIP y SAX del sistema; no se añade una biblioteca de hojas de cálculo. Gráficas con Canvas de Compose. PDF/impresión con APIs Android. No SDKs de Google Sign-In, pagos, analítica o anuncios. Versiones fijadas y verificadas en conjunto, no una afirmación de que todas sean las más recientes. Lint puede recomendar actualizaciones; validar la compatibilidad y repetir las pruebas al actualizar.

No se incluyen SDK, JDK ni cachés descargados dentro del repositorio. Android Studio/Gradle resuelven dependencias desde Google Maven y Maven Central. La app instalada no incluye permiso de Internet.
