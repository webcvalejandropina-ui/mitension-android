# Referencia visual compartida con iPhone

La app iOS es la referencia de diseño; este cambio solo modifica Android.

- Fondo claro `#F2F7F8` y oscuro `#0C1218`.
- Tinta `#071826`, acento claro `#0B796E` y oscuro `#4FDEC8`.
- Tarjeta de última toma: degradado `#071826` → `#0C3441`, valores grandes y texto blanco.
- Tarjetas con radios de 16–24 dp; separación de 18 dp y márgenes de 16 dp en el resumen.
- Menú inferior de cuatro secciones, selección redondeada y navegación horizontal por gesto tanto en páginas como en el menú.
- Última toma, media móvil de siete días y número de tomas; filtros de histórico y consulta.
- Vista médica compacta por día, con mañana/noche dentro y exportación del periodo seleccionado.
- Las funciones y los datos permanecen nativos de Android. No se sustituye el almacén ni se modifica iOS.

Las fuentes, los diálogos de permisos, los selectores y la impresión conservan el comportamiento nativo de cada sistema. No se afirma identidad píxel a píxel: necesita comparación visual en teléfonos de ambos sistemas, además de revisar texto grande y RTL.

La comprobación visual local usa el emulador Android TV API 34 existente con resolución 1080×2400 y densidad 420; esta configuración no convierte su sistema en un teléfono Android. Las imágenes de comprobación deben indicar esa limitación y no contener datos de pacientes.
