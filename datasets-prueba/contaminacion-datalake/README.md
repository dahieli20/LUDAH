# Pruebas para contaminar el Data Lake

Estos archivos estan pensados para subir el riesgo del `DSI` durante pruebas.
La idea es simular un Data Lake degradado con baja calidad, redundancia parcial y
duplicidad exacta.

## Escenario A: contaminar usando la aplicacion

Este escenario usa la app normal. Algunos archivos entran directo a `raw/` y otros
quedan en `review/`; para contaminar el Data Lake activo, apruebalos manualmente.

Orden recomendado:

1. `01_clientes_contaminados_nulos.csv`
   - Esperado: `ACCEPTED`
   - Motivo: tiene valores nulos, pero menos del 40%, por eso entra a `raw/`.
   - Impacto: aumenta `Q - Quality Risk`.

2. `02_ventas_filas_duplicadas.csv`
   - Esperado: `ACCEPTED`
   - Motivo: tiene filas duplicadas internas. El flujo actual no lo bloquea.
   - Impacto: aumenta `Q - Quality Risk`.

3. `03_redundancia_base.csv`
   - Esperado: `ACCEPTED`
   - Motivo: dataset base para comparar redundancia.

4. `04_redundancia_70_review.csv`
   - Esperado: `REVIEW`
   - Motivo: comparte 7 de 10 filas con `03_redundancia_base.csv`.
   - Impacto: si lo apruebas, sube `R - Redundancy Risk`.

5. `05_redundancia_90_review.csv`
   - Esperado: `REVIEW`
   - Motivo: comparte 9 de 10 filas con `03_redundancia_base.csv`.
   - Impacto: si lo apruebas, sube mas `R - Redundancy Risk`.

6. `06_redundancia_100_review.csv`
   - Esperado: `REVIEW`
   - Motivo: tiene las mismas filas de datos que el dataset base, pero encabezado distinto.
   - Impacto: si lo apruebas, genera riesgo maximo de redundancia parcial.

Despues de aprobar los archivos en `REVIEW`, entra a la pantalla `Estado Data Lake`
y presiona `Actualizar`.

## Escenario B: contaminar raw/ con duplicados exactos

La app rechaza duplicados exactos para proteger el Data Lake. Por eso, para probar
`D - Duplication Risk` dentro de `raw/`, carga directamente estos archivos en MinIO,
en la carpeta `raw/`:

- `07_duplicado_exacto_a.csv`
- `08_duplicado_exacto_b.csv`
- `09_duplicado_exacto_c.csv`

Estos tres archivos tienen exactamente el mismo contenido. Si los tres estan en `raw/`,
el factor `D` deberia aumentar porque:

```text
D = (N - H) / N
```

Donde `N` es la cantidad de datasets y `H` la cantidad de hashes unicos.

## Escenario C: contaminacion mixta fuerte

Para un Data Lake bastante contaminado:

1. Sube por la app los archivos `01` al `06`.
2. Aprueba manualmente los archivos que quedaron en `REVIEW`.
3. Carga directamente en MinIO, dentro de `raw/`, los archivos `07`, `08` y `09`.
4. Abre `Estado Data Lake` y presiona `Actualizar`.

Con esto deberias ver riesgo en:

- `Q`, por nulos y filas duplicadas internas.
- `D`, por duplicados exactos en raw/.
- `R`, por redundancia parcial aprobada en raw/.
- `IRI`, por archivos que pasaron por review o rejected durante el flujo.
