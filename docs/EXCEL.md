# Formato Excel

Usar `.xlsx` exportado por Mi Tensión Android o iOS como plantilla. Primera hoja `sheet1.xml`, primera fila de cabeceras y orden estable. G debe tener cabecera `ID`. No fórmulas, `.xls`, CSV ni JSON.

| Columna | Formato |
|---|---|
| A | Fecha/hora real de Excel, no texto. Obligatoria. |
| B | Momento informativo; no prevalece sobre la hora local. |
| C | Sistólica entera sin unidad, 40–300. Obligatoria. |
| D | Diastólica entera sin unidad, 30–200, menor que C. Obligatoria. |
| E | Pulso entero 20–250 o vacío. |
| F | Notas opcionales. |
| G | UUID completo único de la toma. Obligatorio. |
| H | Medicamentos en texto opcional. |
| I–K | Metadatos ocultos para fecha exacta y medicamentos. Conservar en copias; dejar vacíos en nuevas filas manuales. |

Cada fila desde la segunda es una toma independiente: una fila para una toma, dos para dos, tres para tres. UUID distintos. No se exige completar tres ni se promedia. Antes de las 14:00 locales se agrupa como mañana y desde las 14:00 como noche.

Ejemplo: filas con A = 27/09/2026 08:00 y 27/09/2026 08:02 (valores de fecha, no texto), C/D = 120/80 y 118/78 y UUID diferentes quedarán juntas como dos tomas del mismo día por la mañana. Una de 20:55 será noche aunque B diga mañana.

Reimportar conserva los registros existentes y no los edita. Archivo máximo 32 MiB y expansión ZIP máxima 64 MiB. CRC y directorio ZIP comprobados. XML UTF-8 sin declaraciones DTD/entidades. Rechazo completo de datos inválidos antes de escribir. El detalle JSON de medicamentos va dentro del XLSX: no es una exportación de archivo JSON.
