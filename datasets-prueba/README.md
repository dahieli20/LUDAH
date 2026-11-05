# Datasets de prueba

Este conjunto de archivos permite probar el flujo del prototipo y, al mismo tiempo,
entender como se calculan los indicadores definidos en el paper.

## 1. Data Swamp Index (DSI)

El paper propone el `Data Swamp Index (DSI)` como un indice cuantitativo para estimar
el riesgo de degradacion de un Data Lake. El valor final se expresa en una escala de
0 a 100:

```text
DSI = 100 x (0,30Q + 0,25D + 0,25R + 0,20M)
```

Donde:

- `Q = Quality Risk`: riesgo por baja calidad de datos.
- `D = Duplication Risk`: riesgo por duplicidad exacta.
- `R = Redundancy Risk`: riesgo por redundancia parcial.
- `M = Metadata Risk`: riesgo por falta de metadatos.

Todos los factores se interpretan en escala normalizada de 0 a 1:

- `0`: ausencia de riesgo.
- `1`: riesgo maximo.

En la interfaz del prototipo esos valores se muestran como porcentajes de 0 a 100.
Por ejemplo, `Q = 0,20` en la formula equivale a `20%` en la pantalla.

## 2. Version reducida implementada: DSI

Como la primera version del prototipo analiza principalmente archivos CSV y todavia
no implementa una base formal de metadatos, se utiliza la version reducida del indice:

```text
DSI = 100 x (0,35Q + 0,30D + 0,35R)
```

Esta es la formula que calcula actualmente la tarjeta principal de la aplicacion.

La idea intuitiva es:

- Si los archivos en `raw/` tienen mala calidad, sube `Q`.
- Si en `raw/` hay duplicados exactos, sube `D`.
- Si en `raw/` hay archivos muy parecidos entre si, sube `R`.
- El resultado combinado es el `DSI`.

Importante: el `DSI` mide el estado del Data Lake activo, por eso se calcula sobre
los datasets almacenados en `raw/`. Los archivos que quedan en `review/` o `rejected/`
no contaminan directamente el Data Lake activo porque todavia no fueron incorporados.

## 3. Q - Quality Risk

El factor `Q` mide problemas internos de calidad de cada dataset.

Para cada dataset `i`, el paper define:

```text
Qi = 0,50Ni + 0,30Fi + 0,20Ci
```

Donde:

- `Ni`: proporcion de valores nulos del dataset `i`.
- `Fi`: proporcion de filas duplicadas internas del dataset `i`.
- `Ci`: vale `1` si existen columnas duplicadas y `0` si no existen.

Caso especial:

```text
Qi = 1
```

Esto se aplica cuando el dataset no contiene filas de datos. La intuicion es simple:
un archivo sin datos utiles representa el riesgo maximo de calidad.

El factor global de calidad del Data Lake se calcula como promedio:

```text
Q = (1 / N) x sumatoria(Qi)
```

Ejemplo intuitivo:

- Un CSV completo, sin nulos y sin duplicados internos aporta poco riesgo.
- Un CSV con muchos nulos o columnas repetidas aporta mas riesgo.
- Un CSV sin filas utiles aporta el riesgo maximo de calidad.

## 4. D - Duplication Risk

El factor `D` mide duplicidad exacta usando el hash global de cada archivo.

Formula:

```text
D = (N - H) / N
```

Donde:

- `N`: cantidad total de datasets evaluados.
- `H`: cantidad de hashes globales unicos.

Ejemplo:

```text
N = 10 datasets
H = 8 hashes unicos
D = (10 - 8) / 10 = 0,20
```

Interpretacion: el 20% del repositorio evaluado presenta riesgo por duplicidad exacta.

En el flujo controlado del prototipo, un duplicado exacto normalmente va a `rejected/`.
Por eso puede subir el `IRI`, pero no necesariamente sube el `DSI`, salvo que ese
duplicado sea aprobado manualmente y termine en `raw/`.

## 5. R - Redundancy Risk

El factor `R` mide redundancia parcial. Es decir, detecta datasets que no son identicos,
pero comparten muchas filas.

Primero se calcula la similitud entre dos datasets `i` y `j`:

```text
Similitud(i, j) = filas comunes entre i y j / min(filas de i, filas de j)
```

Se usa el minimo de filas porque un archivo pequeno puede estar casi completamente
contenido dentro de otro archivo mas grande.

Luego esa similitud se transforma en riesgo:

```text
Similitud menor a 65%  -> riesgo 0
Similitud 65% - 79%    -> riesgo 0,45
Similitud 80% - 94%    -> riesgo 0,70
Similitud 95% - 99%    -> riesgo 0,90
Similitud 100%         -> riesgo 1,00
```

Para cada dataset se toma el mayor riesgo encontrado contra los demas:

```text
Ri = max Riesgo(Similitud(i, j)), con j distinto de i
```

Luego se calcula el promedio global:

```text
R = (1 / N) x sumatoria(Ri)
```

Ejemplo intuitivo:

- Dos archivos completamente distintos no aumentan `R`.
- Dos archivos con 70% de filas comunes generan riesgo medio.
- Dos archivos casi iguales generan riesgo alto.

## 6. M - Metadata Risk

El factor `M` mide falta de metadatos.

Formula propuesta en el paper:

```text
Mi = metadatos faltantes del dataset i / metadatos requeridos
```

Y el promedio global:

```text
M = (1 / N) x sumatoria(Mi)
```

Metadatos minimos sugeridos:

- Nombre del archivo.
- Ruta dentro del Data Lake.
- Fecha de carga.
- Hash global.
- Cantidad de filas.
- Cantidad de columnas.
- Estado del analisis.
- Fuente u origen.
- Descripcion del dataset.

En esta version del prototipo, `M` queda como extension futura porque todavia no se
implementa una base de datos o catalogo formal de metadatos.

## 7. Ingestion Risk Index (IRI)

El `Ingestion Risk Index (IRI)` es un indicador complementario. No mide la contaminacion
real del Data Lake activo, sino que tan problematicos fueron los archivos que intentaron
ingresar al sistema.

Formula:

```text
IRI = ((archivos en review x 50) + (archivos en rejected x 100)) / total procesados
```

Interpretacion:

- Un archivo en `raw/` suma 0 al `IRI` porque fue aceptado.
- Un archivo en `review/` suma riesgo medio porque requiere revision humana.
- Un archivo en `rejected/` suma riesgo alto porque fue claramente problematico.

La diferencia principal es:

```text
DSI -> mide contaminacion del Data Lake activo, principalmente raw/
IRI    -> mide riesgo del flujo de archivos que intentan ingresar
```

## 8. Escala de interpretacion

La escala propuesta para interpretar el `DSI` es:

```text
0 - 30    Data Lake saludable
31 - 60   Zona frontera
61 - 100  Data Swamp
```

Lectura intuitiva:

- `Data Lake saludable`: bajo nivel de contaminacion.
- `Zona frontera`: ya existen senales de degradacion.
- `Data Swamp`: alto riesgo de perdida de utilidad y confiabilidad.

## 9. Flujo del prototipo

Cada CSV pasa por este recorrido:

```text
1. El usuario sube un CSV.
2. El backend valida el formato.
3. Se calculan metricas de calidad.
4. Se calcula el hash global.
5. Se calculan hashes por fila.
6. Se compara con datasets existentes en raw/.
7. Se clasifica como ACCEPTED, REVIEW o REJECTED.
8. Se almacena en raw/, review/ o rejected/.
9. Se recalculan DSI e IRI.
```

Zonas del Data Lake simulado:

- `raw/`: datasets aceptados que forman parte del Data Lake activo.
- `review/`: datasets dudosos que requieren decision manual.
- `rejected/`: datasets rechazados que no deben incorporarse.
- `metadata/`: zona prevista para registrar resultados de analisis o metadatos.

## 10. Orden recomendado de prueba

1. `01_clientes_base.csv`
   - Esperado: `ACCEPTED`
   - Motivo: CSV nuevo y con calidad aceptable.
   - Impacto esperado: entra a `raw/`; el `DSI` deberia mantenerse bajo.

2. `02_clientes_base_duplicado_exacto.csv`
   - Esperado: `REJECTED`
   - Motivo: mismo contenido que `01_clientes_base.csv`.
   - Formula relacionada: `D = (N - H) / N`.
   - Impacto esperado: sube el `IRI`; no deberia subir directamente el `DSI` porque queda en `rejected/`.

3. `03_clientes_redundante_parcial.csv`
   - Esperado: `REVIEW`
   - Motivo: comparte 4 de 6 filas con el dataset base, es decir 66,67% de similitud.
   - Formula relacionada: `Similitud(i,j) = filas comunes / min(filas i, filas j)`.
   - Impacto esperado: sube el `IRI`; si se aprueba manualmente, pasa a `raw/` y puede subir `R`.

4. `04_columnas_duplicadas.csv`
   - Esperado: `REVIEW`
   - Motivo: contiene columnas duplicadas.
   - Formula relacionada: `Qi = 0,50Ni + 0,30Fi + 0,20Ci`, con `Ci = 1`.

5. `05_muchos_nulos.csv`
   - Esperado: `REVIEW`
   - Motivo: supera el 40% de valores nulos.
   - Formula relacionada: `Ni`, proporcion de valores nulos dentro de `Qi`.

6. `06_solo_encabezado.csv`
   - Esperado: `REJECTED`
   - Motivo: no contiene filas de datos.
   - Formula relacionada: caso especial `Qi = 1`.

7. `07_filas_duplicadas_internas.csv`
   - Esperado: `ACCEPTED`
   - Motivo: entra a `raw/`, pero tiene filas duplicadas internas.
   - Formula relacionada: `Fi`, proporcion de filas duplicadas internas dentro de `Qi`.
   - Impacto esperado: puede subir el factor `Q` del `DSI`.

Notas:

- Para probar duplicidad exacta, primero carga `01_clientes_base.csv` y despues `02_clientes_base_duplicado_exacto.csv`.
- Para probar redundancia parcial, primero carga `01_clientes_base.csv` y despues `03_clientes_redundante_parcial.csv`.
- Si apruebas manualmente `03_clientes_redundante_parcial.csv`, pasara a `raw/` y el factor `R` del `DSI` deberia aumentar.
