# Instalación y ejecución

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 21 o superior | Servidor (solo opción B) |
| Maven | 3.9+ (o el de IntelliJ) | Compilar el servidor (solo opción B) |
| Node.js | 20 o superior | Clientes Angular (solo opción B) |
| Docker Desktop | cualquiera reciente | Kafka y RabbitMQ (y, en la opción A, todo lo demás) |
| Cuenta de Supabase | gratuita | Base de datos de comandos (PostgreSQL) |

## Opción A: todo con Docker (la más corta)

No instalas Kafka, RabbitMQ, MongoDB, Java ni Node: solo **Docker Desktop**.

| Paso | Qué | Comando |
|---|---|---|
| 1 | Base de datos | Crear el proyecto en Supabase ([guía](Base-de-datos-Supabase)) |
| 2 | Variables | Copiar `.env.example` a `.env` y poner los datos de Supabase |
| 3 | Levantar todo (Kafka, RabbitMQ, MongoDB, servidor y clientes) | `docker compose --profile app up -d --build` |
| 4 | Ver los logs del servidor | `docker logs -f tienda-backend` |
| 5 | Apagar | `docker compose --profile app down` |

La primera vez tarda unos minutos porque construye las imágenes. Los clientes quedan en http://localhost:4200 (Tienda) y http://localhost:4201 (Panel).

## Opción B: Docker solo para Kafka, RabbitMQ y MongoDB

Sirve si quieres ejecutar el servidor desde IntelliJ para depurarlo.

| Paso | Qué | Comando |
|---|---|---|
| 1 | Base de datos | Crear el proyecto en Supabase ([guía](Base-de-datos-Supabase)) |
| 2 | Kafka, RabbitMQ y MongoDB | `docker compose up -d` (en la raíz del repo) |
| 3 | Servidor | Definir variables y `mvn spring-boot:run` (carpeta `backend`) |
| 4 | Cliente Tienda | `npm install` y `npx ng serve tienda` (carpeta `frontend`) |
| 5 | Cliente Panel | `npx ng serve admin` (carpeta `frontend`) |

## Variables del servidor (opción B)

En la opción A no hace falta: el servidor las lee del archivo `.env`.

**Windows (CMD):**

```
set DB_URL=jdbc:postgresql://aws-0-REGION.pooler.supabase.com:5432/postgres?sslmode=require
set DB_USER=postgres.TU_PROJECT_REF
set DB_PASSWORD=tu_clave
mvn spring-boot:run
```

**IntelliJ:** *Run → Edit Configurations → TiendaApplication → Environment variables*, y pega `DB_URL=...;DB_USER=...;DB_PASSWORD=...`.

Kafka, RabbitMQ y MongoDB usan sus valores por defecto (`localhost:9092`, `localhost:5672` con usuario `guest`, y `mongodb://localhost:27017/tienda`).

## Si ya tienes RabbitMQ instalado en Windows

El `docker-compose.yml` también usa el puerto 5672. No levantes los dos a la vez:

```
docker compose up -d kafka
```

y usa tu RabbitMQ nativo (panel en http://localhost:15672). El servidor crea los exchanges, colas y bindings al arrancar.

## Direcciones

| Qué | URL |
|---|---|
| Tienda | http://localhost:4200 |
| Panel | http://localhost:4201 |
| API | http://localhost:8080/api/consultas/resumen |
| Salud del servidor | http://localhost:8080/actuator/health |
| Panel de RabbitMQ | http://localhost:15672 |
| MongoDB | `docker exec -it mongo mongosh tienda --eval "db.pedido_vista.find()"` |

## Si cambias la dirección del servidor

Edita `frontend/projects/shared/src/config.ts` (una sola línea) y, en el servidor, la variable `CORS_ORIGENES` con las direcciones de los clientes.

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| El servidor no arranca y habla de la base de datos | `DB_URL`, usuario o clave incorrectos; usa el Session pooler |
| Los pedidos se crean pero no aparecen en "Mis pedidos" | Kafka apagado: el evento queda en el outbox y se publica al volver |
| Los clientes dicen "Sin conexión en vivo" | Servidor apagado o puerto 8080 ocupado |
| Con Docker, el servidor se reinicia una y otra vez | `docker logs tienda-backend`: casi siempre es `DB_URL`, `DB_USER` o `DB_PASSWORD` mal escritos en `.env` |
| Con Docker, el servidor no conecta con MongoDB | Revisa que el contenedor `mongo` esté en `docker compose --profile app ps` |
| Con Docker, el servidor no conecta con Kafka | Espera unos segundos: el servidor reintenta hasta que Kafka esté listo |
| Error de CORS en el navegador | El origen del cliente no está en `CORS_ORIGENES` |
| `PRECONDITION_FAILED` en RabbitMQ | Hay una cola con el mismo nombre pero otra configuración: bórrala en el panel |
