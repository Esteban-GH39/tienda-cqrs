# Bases de datos: PostgreSQL (Supabase) y MongoDB

El proyecto usa **dos motores distintos**, uno por cada lado de CQRS:

| Lado | Motor | Dónde corre | Para qué |
|---|---|---|---|
| Comandos (escribir) | PostgreSQL | Supabase (nube) | Transacciones y reglas del negocio |
| Consultas (leer) | MongoDB | Contenedor `mongo` (Docker) | Lecturas rápidas de documentos |

Esta página explica primero PostgreSQL en Supabase y al final MongoDB.

## Por qué Supabase

| Criterio | Supabase |
|---|---|
| Es PostgreSQL estándar | Sí: se conecta con JDBC, sin código especial |
| Plan gratuito | Sí, sin tarjeta (los límites del plan pueden cambiar: revísalos al crear el proyecto) |
| Panel para ver tablas y datos | Sí, **Table Editor** y **SQL Editor** |
| Compartible por el grupo | Sí, desde cualquier equipo con la cadena de conexión |

Otras opciones válidas: Neon (también PostgreSQL gratuito), Render o Railway (permiten alojar además el servidor), AWS RDS o Azure (de pago).

## Crear la base

1. Entra a https://supabase.com e inicia sesión.
2. **New project**: nombre, **contraseña de base de datos** (guárdala), región cercana.
3. Espera a que termine de crearse.
4. Pulsa **Connect** (arriba) y elige **Session pooler**. Copia el host y el usuario.

## Cadena de conexión

| Variable | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://aws-0-REGION.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DB_USER` | `postgres.TU_PROJECT_REF` (el usuario del pooler lleva el id del proyecto) |
| `DB_PASSWORD` | la contraseña que elegiste |

Usa el **Session pooler** (puerto 5432) y no la conexión directa: la directa de Supabase usa IPv6 y muchas redes no la tienen. Evita el *Transaction pooler* (puerto 6543): no soporta sentencias preparadas y puede dar errores con JDBC y Flyway.

Copia el valor exacto desde el botón **Connect**, porque el host cambia según la región.

## Qué se crea

Al arrancar, Flyway ejecuta `V1__esquema.sql`:

| Esquema | Tabla | Para qué |
|---|---|---|
| `comando` | `pedido` | Pedido (modelo de escritura) |
| `comando` | `pedido_item` | Líneas del pedido |
| `comando` | `outbox_evento` | Eventos pendientes de publicar en Kafka |

En **Table Editor** cambia el esquema del selector (arriba a la izquierda) a `comando` para ver las tablas. El lado de consultas ya no está aquí: vive en MongoDB.

## Buenas prácticas

- No subas la contraseña a GitHub: usa variables de entorno (`.env` está en `.gitignore`).
- Si el proyecto gratuito se pausa por inactividad, se reactiva desde el panel.

---

# MongoDB (lado de consultas)

| Dato | Valor |
|---|---|
| Imagen | `mongo:7` (contenedor `mongo`, puerto 27017) |
| Base | `tienda` |
| Conexión | variable `MONGO_URI` (por defecto `mongodb://localhost:27017/tienda`; dentro de Docker, `mongodb://mongo:27017/tienda`) |
| Se crea solo | Sí: MongoDB crea la base y las colecciones al guardar el primer dato |

| Colección | Qué guarda |
|---|---|
| `pedido_vista` | Un documento por pedido: `cliente`, `total`, `estado`, `items` (lista adentro), `creadoEn`, `actualizadoEn` |
| `evento_aplicado` | El `_id` de cada evento ya aplicado (idempotencia) |

Ejemplo de documento:

```json
{
  "_id": "3f2c...",
  "cliente": "Ana",
  "total": "1285.00",
  "estado": "PAGADO",
  "items": [ { "producto": "Laptop", "cantidad": 1, "precioUnitario": "1200.00" } ],
  "creadoEn": "2026-11-14T20:10:00Z",
  "actualizadoEn": "2026-11-14T20:11:30Z"
}
```

## Ver los datos

```
docker exec -it mongo mongosh tienda --eval "db.pedido_vista.find()"
```
```
docker exec -it mongo mongosh tienda --eval "db.pedido_vista.countDocuments({estado:'PAGADO'})"
```
Para el SQL de Supabase usa su **Table Editor**; para MongoDB, el comando de arriba o la aplicación gratuita *MongoDB Compass* conectada a `mongodb://localhost:27017`.

## Por qué MongoDB para leer

Un pedido con sus ítems es un solo documento: la pantalla lo trae con una lectura, sin joins. Y como la lectura está separada de la escritura, puede crecer sin afectar a PostgreSQL.

