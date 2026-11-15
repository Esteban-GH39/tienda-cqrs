# Tienda en vivo – Wiki

Bienvenido. Esta wiki explica el proyecto de **Sistemas Distribuidos**: una tienda con servidor en Spring Boot, dos clientes Angular, CQRS, Kafka, RabbitMQ, broadcast por WebSocket y dos motores de base de datos (PostgreSQL en Supabase y MongoDB).

## Páginas

| Página | Para qué sirve |
|---|---|
| [Arquitectura](Arquitectura) | Cómo encajan todas las piezas y el recorrido de un pedido |
| [CQRS](CQRS) | Qué es, cómo se separó comando y consulta, y qué costo tiene |
| [Kafka](Kafka) | Por qué y cómo se usa en el proyecto |
| [RabbitMQ](RabbitMQ) | Exchanges, colas, bindings y routing keys |
| [Broadcast y WebSocket](Broadcast-y-WebSocket) | Cómo llega el mismo mensaje a todos los clientes |
| [Base de datos Supabase](Base-de-datos-Supabase) | Los dos motores: PostgreSQL en la nube (comandos) y MongoDB (consultas) |
| [Instalación y ejecución](Instalacion-y-ejecucion) | Paso a paso para correr todo |
| [API](API) | Endpoints de comandos y consultas, y el WebSocket |
| [Pruebas](Pruebas) | Pruebas automáticas y demostración manual |

El documento completo de requisitos, con la tabla "qué tema va dónde y por qué", está en `docs/REQUERIMIENTOS.md` del repositorio.
