# LUDAH

LUDAH ayuda a identificar señales de degradación en un Data Lake mediante el análisis de calidad, duplicidad exacta y redundancia parcial de archivos CSV.

La aplicación permite cargar y clasificar archivos, resolver casos en revisión y auditar el contenido almacenado. El IRI describe la distribución actual de los archivos entre las zonas del flujo de ingreso; el DSI resume los factores de riesgo del conjunto auditado.

## Componentes

| Componente | Tecnología | Función |
| --- | --- | --- |
| Interfaz | Angular 21 | Inicio, carga, revisión y auditoría |
| Backend | Java 17 y Spring Boot 3.5 | Análisis, decisiones y cálculo de indicadores |
| Almacenamiento | MinIO | Archivos CSV organizados por rutas |
| Base de datos | PostgreSQL 17 | Registros de procesamiento, auditorías y umbrales |
| Migraciones | Flyway | Creación y actualización del esquema al iniciar el backend |

## Requisitos

- Docker Desktop iniciado, con Docker Compose disponible. En Linux, Docker Engine y el plugin de Compose.
- JDK 17, con `java` disponible en la terminal y `JAVA_HOME` apuntando al JDK si está definido.
- Node.js 22, versión 22.12 o posterior dentro de esa rama, y npm. Angular 21 también admite Node 20.19 o posterior de la rama 20 y Node 24 o superior.
- Conexión a Internet para descargar imágenes, Maven y dependencias durante la primera preparación.

El repositorio incluye Maven Wrapper y Angular CLI como dependencia local. Los comandos siguientes usan esas herramientas.

Comprobar desde la terminal donde se ejecutará el proyecto:

```text
java -version
node --version
npm --version
docker version
docker compose version
```

Abrir la carpeta raíz de LUDAH, que contiene `backend/`, `frontend/` y `docker/`. Los ejemplos de cada sección parten de esa raíz, salvo que se indique otra ubicación.

## 1. Iniciar PostgreSQL y MinIO

Desde la raíz, tanto en PowerShell como en Bash/WSL:

```sh
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

Este comando inicia ambos servicios. Esperar a que PostgreSQL figure como `healthy` antes de iniciar el backend. Para comprobar su disponibilidad:

```sh
docker compose -f docker/docker-compose.yml exec -T postgres pg_isready -U ludah -d ludah
```

### Puertos y credenciales locales

Estos valores corresponden a `docker/docker-compose.yml` y a la configuración del backend incluida en el repositorio.

| Servicio | Dirección desde el equipo | Usuario | Contraseña |
| --- | --- | --- | --- |
| Aplicación web | http://localhost:4200 | — | — |
| Backend | http://localhost:8080/api | — | — |
| Consola de MinIO | http://localhost:9002 | `admin` | `admin123` |
| API de MinIO | http://localhost:9000 | `admin` | `admin123` |
| PostgreSQL | `localhost:5434`, base `ludah` | `ludah` | `ludah123` |

El puerto interno de la consola de MinIO es 9001, publicado en el equipo como **9002**. PostgreSQL usa internamente 5432, publicado como **5434**.

### Crear el bucket de MinIO una sola vez

1. Abrir http://localhost:9002 e ingresar con las credenciales de la tabla.
2. Crear el bucket **`data-lake`** si todavía no existe.

Las rutas `raw/`, `review/` y `rejected/` se crean al guardar los archivos correspondientes. Para auditar un repositorio independiente, se pueden cargar CSV directamente en otra carpeta del mismo bucket, por ejemplo `experimentos/escenario_1/`.

## 2. Iniciar el backend

Abrir una terminal en la raíz del proyecto y mantenerla abierta mientras se utiliza LUDAH.

**PowerShell / Windows:**

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

**Bash / WSL / Linux:**

```sh
cd backend
bash mvnw spring-boot:run
```

El Wrapper descarga Maven si hace falta. Spring Boot conecta con PostgreSQL y Flyway aplica las migraciones pendientes automáticamente. Esperar a que el registro muestre `Started Application` y que Tomcat haya iniciado en el puerto 8080.

Para comprobar la conexión con MinIO, abrir:

```text
http://localhost:8080/api/minio/test
```

Con el bucket creado, la respuesta debe indicar que la conexión es correcta y que encontró `data-lake`.

Para consultar las carpetas disponibles sin ejecutar una auditoría:

```text
http://localhost:8080/api/minio/prefixes
```

## 3. Preparar e iniciar el frontend

Abrir **otra terminal** en la raíz del proyecto.

**Primera instalación o después de cambiar las dependencias:**

```sh
cd frontend
npm ci
npm start
```

**Si las dependencias ya están instaladas y no cambiaron:**

```sh
cd frontend
npm start
```

Estos comandos funcionan en PowerShell y en Bash/WSL. Mantener la terminal abierta y esperar a que Angular indique que el servidor está disponible.

Abrir **http://localhost:4200/**. La aplicación muestra la pantalla de inicio con los accesos a ingestión y auditoría.

Si se trabaja en WSL, ejecutar Java y Node desde el entorno donde estén instalados. En caso de necesitar que el servidor de desarrollo escuche fuera de su dirección local:

```sh
npm start -- --host 0.0.0.0
```

Para el uso habitual desde el propio equipo, abrir la aplicación mediante `http://localhost:4200`, que es el origen permitido por la configuración CORS del backend.

## Volver a levantar el proyecto otro día

Con Docker disponible y las dependencias instaladas:

1. Desde la raíz, ejecutar `docker compose -f docker/docker-compose.yml up -d` y esperar a que PostgreSQL esté disponible.
2. Iniciar el backend con el comando de la sección 2.
3. En otra terminal, ejecutar `npm start` desde `frontend/`.
4. Abrir http://localhost:4200/.

El bucket y los datos existentes se conservan en el almacenamiento persistente configurado en Docker.

## Uso de la herramienta

- **Inicio:** explica el propósito de LUDAH y permite elegir una tarea.
- **Ingestión de datos:** agregar CSV y pulsar «Subir y analizar». Los resultados pueden ser Aceptado, En revisión o Rechazado.
- **Archivos de la sesión:** consultar los resultados de los archivos de la sesión actual y aprobar o rechazar los casos en revisión. Esta lista se mantiene en memoria mientras la aplicación está abierta; al recargar, se pierde la lista de la interfaz, aunque los objetos y registros persistidos permanecen almacenados.
- **Auditoría del Data Lake:** consultar el diagnóstico global o seleccionar una ruta de MinIO y ejecutar una auditoría. La lista de rutas se actualiza automáticamente cada 15 segundos mientras esa modalidad está seleccionada y la pestaña está visible; también se consulta al volver a la ventana.
- **Configurar escala:** desde ingestión, ajustar los límites de clasificación del DSI. Cada auditoría conserva los umbrales utilizados.

En la auditoría global, el DSI evalúa los CSV de `raw/` y el IRI considera la distribución entre `raw/`, `review/` y `rejected/`. En la auditoría por ruta, el IRI aparece como «No aplica».

La regla de rechazo por nombre repetido en `raw/` se mantiene, incluso cuando el contenido de la nueva carga sea diferente. Los códigos internos `ACCEPTED`, `REVIEW` y `REJECTED` se presentan con etiquetas en español en la interfaz.

## Detener los servicios

Detener frontend y backend con `Ctrl+C` en sus respectivas terminales.

Desde la raíz, detener los contenedores conservando su almacenamiento:

```sh
docker compose -f docker/docker-compose.yml stop
```

## Configuración

| Archivo | Qué configurar |
| --- | --- |
| `docker/docker-compose.yml` | Puertos, credenciales y almacenamiento de PostgreSQL y MinIO |
| `backend/src/main/resources/application.properties` | Puerto del backend, conexión a PostgreSQL, conexión y bucket de MinIO |
| `frontend/src/environments/environment.ts` | URL del backend usada por Angular |
| `frontend/angular.json` | Opciones de compilación y servidor de desarrollo |

La conexión PostgreSQL permite estas variables de entorno:

| Variable | Valor predeterminado |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5434/ludah` |
| `DATABASE_USERNAME` | `ludah` |
| `DATABASE_PASSWORD` | `ludah123` |

Si se cambia un puerto o una credencial, actualizar también los componentes que utilizan ese servicio. Por ejemplo, cambiar el puerto del backend requiere ajustar su URL en `frontend/src/environments/environment.ts`.

MinIO conserva los objetos en `docker/minio-data/`. PostgreSQL conserva sus datos en el volumen Docker `postgres-data`, con el nombre efectivo asignado por Compose.

## Problemas frecuentes

### «Port 8080 was already in use»

Hay otro proceso utilizando el puerto del backend. Si LUDAH ya está funcionando, utilizar esa instancia. Para reiniciarla, detener el backend en su terminal antes de ejecutar otra vez el comando de arranque.

Para identificar el proceso en Windows/PowerShell:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress, LocalPort, OwningProcess
```

En WSL/Linux:

```sh
ss -ltnp 'sport = :8080'
```

### El backend no conecta con PostgreSQL

Comprobar que `postgres-ludah` está disponible, que su comprobación de salud pasa y que la URL utiliza el puerto **5434** desde el equipo. Revisar sus registros desde la raíz:

```sh
docker compose -f docker/docker-compose.yml logs --tail 80 postgres
```

### MinIO no abre o no encuentra el bucket

Abrir la consola en **http://localhost:9002** y verificar que exista `data-lake`. El backend conecta con la API en **http://localhost:9000**.

```sh
docker compose -f docker/docker-compose.yml logs --tail 80 minio
```

### La interfaz no refleja cambios recientes

El servidor Angular tiene activada la detección periódica de cambios. Si sigue mostrando contenido anterior, recargar con `Ctrl+F5` o reiniciar `npm start`.

### Angular informa que la versión de Node no es compatible

Comprobar `node --version` en la terminal usada para iniciar el frontend y utilizar una de las versiones indicadas en los requisitos. Si se cambió el entorno o las dependencias, ejecutar `npm ci` desde `frontend/`.

## Comprobaciones de desarrollo

Para compilar la interfaz desde `frontend/`:

```sh
npm run build
```

Para ejecutar las pruebas del backend desde `backend/`:

```powershell
.\mvnw.cmd test
```

En Bash/WSL:

```sh
bash mvnw test
```

Las pruebas del backend usan una base H2 de prueba en lugar de la base PostgreSQL de uso habitual.
