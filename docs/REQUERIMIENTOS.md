# Requerimientos del proyecto – Tienda en vivo (CQRS + Kafka + RabbitMQ)

Proyecto de la asignatura **Sistemas Distribuidos** (Universidad Central). Una tienda de pedidos en línea con un servidor en Java y dos aplicaciones cliente en Angular que se actualizan en vivo.

## 1. Requisitos de la asignatura y cómo se cumplen

| # | Requisito | Cómo se cumple | Dónde está |
|---|---|---|---|
| R1 | Conexiones *broadcast* | El servidor empuja cada cambio de un pedido a **todos** los clientes conectados por WebSocket (STOMP), sin que ellos pregunten. El mensaje llega al servidor por un exchange `fanout` de RabbitMQ. | `backend/.../mensajeria/BroadcastWebSocket.java`, `config/WebSocketConfig.java`, `frontend/projects/shared/src/tiempo-real.service.ts` |
| R2 | Pila de mensajería | **Kafka** lleva los eventos del lado de comandos al de consultas; **RabbitMQ** enruta avisos a colas de trabajo y al broadcast. | `backend/.../comando/OutboxPublicador.java`, `consulta/ProyectorKafka.java`, `mensajeria/MensajeriaConfig.java` |
| R3 | Implementar CQRS | Escritura y lectura están separadas: paquetes distintos, API distinta (`/api/comandos` y `/api/consultas`), **motores de base de datos distintos** (PostgreSQL para comandos y MongoDB para consultas) y modelos distintos. | `backend/.../comando/`, `backend/.../consulta/`, `db/migration/V1__esquema.sql`, `consulta/PedidoVistaMongo.java` |
| R4 | Aplicación servidora | Un servidor Spring Boot (Java 21) con la API REST, los productores/consumidores de mensajería y el WebSocket. | `backend/` |
| R5 | Aplicaciones clientes aparte | Dos aplicaciones Angular independientes del servidor y entre sí: **Tienda** (puerto 4200) y **Panel de administración** (puerto 4201). | `frontend/projects/tienda`, `frontend/projects/admin` |
| R6 | En GitHub, con requerimientos y wiki | Este documento, el `README.md` y las páginas de la wiki en `docs/wiki/`, con la guía para publicarlas. | `docs/` |
| R7 | Explicar en qué se coloca cada tema y por qué | Sección 4 de este documento y cada página de la wiki. | `docs/REQUERIMIENTOS.md` §4 |
| R8 | Base de datos PostgreSQL en la nube | **Supabase** (PostgreSQL administrado, plan gratuito) para el lado de comandos. | `docs/wiki/Base-de-datos-Supabase.md`, `application.yml` |
| R9 | Dos motores de base de datos distintos | **PostgreSQL** (comandos) y **MongoDB** (consultas). | `comando/` (JPA) y `consulta/PedidoVistaMongo.java` (MongoDB) |
| R10 | Ver en tiempo real cuándo se usa cada herramienta del stack | Pestaña **Log del stack** en la Tienda y en el Panel: cada pieza del servidor registra su uso y el servidor lo emite por WebSocket (`/topic/traza`). | `traza/` (servidor) y `shared/log-stack.component.ts` (clientes) |

## 2. Requisitos funcionales

| Id | Requisito | Cliente |
|---|---|---|
| RF-01 | El cliente puede armar un carrito con productos de un catálogo y crear un pedido. | Tienda |
| RF-02 | El pedido nace en estado `CREADO`. Se puede pagar (`PAGADO`), enviar (`ENVIADO`, solo si está pagado) o cancelar (`CANCELADO`, solo si está creado o pagado). | Tienda y Panel |
| RF-03 | El cliente ve únicamente sus pedidos ("Mis pedidos"). | Tienda |
| RF-04 | El administrador ve todos los pedidos, puede filtrarlos por estado y ve el conteo por estado. | Panel |
| RF-05 | Cuando cualquiera cambia un pedido, **todos** los clientes abiertos lo ven al instante sin recargar (broadcast). | Tienda y Panel |
| RF-06 | Al crear, pagar, enviar o cancelar, el servidor avisa a los sistemas internos: inventario, facturación y logística. | Servidor |
| RF-07 | Una operación que rompe una regla (por ejemplo, enviar sin pagar) se rechaza con un mensaje claro. | Servidor |

## 3. Requisitos no funcionales

| Id | Requisito | Cómo se logra |
|---|---|---|
| RNF-01 | **No perder eventos** si Kafka está caído. | Patrón *outbox*: el evento se guarda en la misma transacción que el pedido y se publica después, con reintentos. |
| RNF-02 | **No duplicar efectos** si un evento llega dos veces. | Colección `evento_aplicado` en MongoDB (el proyector es idempotente). |
| RNF-03 | **Orden por pedido.** | Clave del mensaje de Kafka = id del pedido, así todos sus eventos van a la misma partición. |
| RNF-04 | Los clientes se **reconectan solos** si se cae el servidor. | `reconnectDelay` de STOMP en el cliente. |
| RNF-05 | Secretos fuera del código. | Variables de entorno (`.env.example`); `.env` está en `.gitignore`. |
| RNF-06 | Esquema reproducible. | Migraciones Flyway versionadas (`db/migration`). |

## 4. Dónde se coloca cada tema y por qué

Esta es la parte central: para cada tecnología o patrón, **dónde está en el proyecto**, **por qué se usa ahí** y **qué pasaría si no estuviera**.

### 4.1 CQRS

| | |
|---|---|
| **Dónde** | Todo el servidor. Mitad de escritura en `comando/` (API `POST /api/comandos/...`, tablas del esquema `comando` en PostgreSQL). Mitad de lectura en `consulta/` (API `GET /api/consultas/...`, colección `pedido_vista` en MongoDB). |
| **Por qué ahí** | Escribir y leer un pedido son problemas distintos. Escribir exige reglas (no enviar sin pagar) y transacciones, por eso usa tablas normalizadas en **PostgreSQL** con JPA. Leer exige rapidez y forma cómoda para la pantalla, por eso usa **documentos en MongoDB** (el pedido con sus ítems adentro, sin joins). |
| **Sin él** | Una sola tabla y un solo modelo para todo: las pantallas se acoplan a las reglas de negocio y, si crece la lectura, hay que escalar también la escritura. |
| **Costo asumido** | *Consistencia eventual*: la vista se actualiza un instante después del comando. Por eso los comandos responden `202 Accepted`. |

### 4.2 Apache Kafka

| | |
|---|---|
| **Dónde** | Tópico `pedidos.eventos` (3 particiones). El que publica es `OutboxPublicador` (lado de comandos) y el que consume es `ProyectorKafka` (lado de consultas, grupo `proyector-pedidos`). |
| **Por qué ahí** | Es el **puente de CQRS**: lleva los eventos de un lado al otro. Kafka es un registro duradero y ordenado: guarda los eventos, mantiene el orden por pedido (clave = id) y permite **volver a reproducirlos** desde el inicio para reconstruir la vista si se daña o si se crea una nueva. |
| **Sin él** | El lado de consultas tendría que leer las tablas de escritura (rompe CQRS) o recibir llamadas directas (se acopla y se pierden datos si está caído). |

### 4.3 RabbitMQ – exchange `topic` (`ex.pedidos`) y colas de trabajo

| | |
|---|---|
| **Dónde** | `ex.pedidos` con routing keys `pedido.creado`, `pedido.pagado`, `pedido.enviado`, `pedido.cancelado`. Tres colas: `q.inventario` (creado y cancelado), `q.facturacion` (pagado), `q.logistica` (enviado). Consumidores en `TrabajadoresRabbit`. |
| **Por qué ahí** | Cada área del negocio solo quiere **algunos** eventos. RabbitMQ es bueno **enrutando**: con routing keys y patrones decide qué cola recibe cada mensaje, y la cola reparte el trabajo entre consumidores. Es la misma lógica de `ex.topico` del proyecto NBA. |
| **Por qué no Kafka para esto** | Kafka no enruta por patrón; tendría un tópico por cada caso o cada consumidor leería todo y descartaría lo que no le sirve. |

### 4.4 RabbitMQ – exchange `fanout` (`ex.broadcast`)

| | |
|---|---|
| **Dónde** | `ex.broadcast` publica cada evento y entrega **una copia a cada cola enlazada**. Cada instancia del servidor tiene una cola anónima propia (`broadcastQueue`), que escucha `BroadcastWebSocket`. |
| **Por qué ahí** | Si se ejecutan varios servidores, un cliente puede estar conectado a cualquiera. El `fanout` asegura que **todas las instancias** se enteren y cada una avise a sus propios clientes WebSocket. Es el `ex.ventas` del proyecto NBA. |
| **Sin él** | Con más de un servidor, solo los clientes del servidor que procesó el evento se enterarían. |

### 4.5 Conexiones broadcast (WebSocket + STOMP)

| | |
|---|---|
| **Dónde** | Servidor: `WebSocketConfig` (endpoint `/ws`) y `BroadcastWebSocket` (envía a `/topic/pedidos`). Clientes: `tiempo-real.service.ts`, usado por Tienda y Panel. |
| **Por qué ahí** | Es la última milla hacia el navegador, y los navegadores no hablan Kafka ni AMQP. El WebSocket mantiene una conexión abierta: el servidor **empuja** el cambio a todos los suscritos. Así no hay que preguntar cada pocos segundos (*polling*). |
| **Qué es "broadcast" aquí** | Un mensaje enviado una vez llega a todos los clientes suscritos al mismo destino (`/topic/pedidos`). |

### 4.6 Patrón Outbox

| | |
|---|---|
| **Dónde** | Tabla `comando.outbox_evento`, escrita por `PedidoComandoServicio` y vaciada por `OutboxPublicador` cada segundo. |
| **Por qué ahí** | Guardar el pedido y publicar en Kafka son dos sistemas distintos y no pueden compartir una transacción. Si se guardara y luego Kafka fallara, el pedido existiría sin evento. Con el outbox, el evento se guarda **en la misma transacción** que el pedido, y se publica después con reintentos. |

### 4.7 Idempotencia del proyector

| | |
|---|---|
| **Dónde** | `ProyeccionServicio` y la colección `evento_aplicado` de MongoDB. |
| **Por qué ahí** | Kafka entrega "al menos una vez": tras un reinicio o un reintento, un evento puede llegar dos veces. Anotar el `eventId` evita aplicar dos veces el mismo cambio. |

### 4.8 PostgreSQL en la nube (Supabase) – lado de COMANDOS

| | |
|---|---|
| **Dónde** | Una base PostgreSQL en Supabase, esquema `comando`: tablas `pedido`, `pedido_item` y `outbox_evento`. Se accede con JPA (`comando/`). |
| **Por qué ahí** | Escribir necesita **transacciones y reglas**: guardar el pedido y su evento del outbox debe ocurrir completo o no ocurrir. PostgreSQL es relacional y transaccional (ACID), justo lo que pide ese lado. Además los datos deben sobrevivir al servidor y ser accesibles desde cualquier equipo del grupo: Supabase es PostgreSQL estándar (JDBC, sin código especial), gratuito y con panel para ver las tablas. |
| **Sin él** | Sin transacciones, un fallo entre guardar el pedido y guardar el evento dejaría datos inconsistentes. |

### 4.8b MongoDB – lado de CONSULTAS

| | |
|---|---|
| **Dónde** | Contenedor `mongo` (base `tienda`). Colección `pedido_vista` (un documento por pedido, con sus ítems adentro) y colección `evento_aplicado` (idempotencia). Se accede con Spring Data MongoDB (`consulta/PedidoVistaMongo.java`). |
| **Por qué ahí** | Leer necesita **rapidez y una forma lista para la pantalla**. Un pedido con sus ítems es naturalmente un documento: una sola lectura lo trae completo, sin joins. Además, la lectura puede crecer y escalar sin tocar la base de escritura. Usar un **motor distinto** deja ver el punto central de CQRS: cada lado elige el almacenamiento que mejor le sirve. |
| **Qué lo alimenta** | Solo los eventos de Kafka (`ProyectorKafka`). Ningún comando escribe aquí directamente. |
| **Costo asumido** | No hay transacciones entre las dos bases, por eso el proyector está hecho con pasos repetibles (idempotentes). Si un evento se procesa a medias, se puede volver a procesar sin dañar nada. |
| **Nota** | Los motores son intercambiables: el lado de consultas depende de la interfaz `PedidoVistaRepositorio`. Cambiar MongoDB por otro almacén solo exigiría una nueva implementación de esa interfaz. |

### 4.9 Flyway (migraciones)

| | |
|---|---|
| **Dónde** | `backend/src/main/resources/db/migration/V1__esquema.sql` (solo PostgreSQL; MongoDB crea sus colecciones solo). |
| **Por qué ahí** | Crea las tablas automáticamente al arrancar el servidor, igual en cada máquina y en la nube. Nadie crea tablas a mano. |

### 4.10 Servidor Spring Boot

| | |
|---|---|
| **Dónde** | `backend/`. |
| **Por qué ahí** | Concentra la lógica de negocio y es el único que habla con la base y con los brokers. Los clientes solo conocen su API REST y su WebSocket. Así se puede cambiar la mensajería o la base sin tocar los clientes. |

### 4.11 Clientes Angular (Tienda y Panel)

| | |
|---|---|
| **Dónde** | `frontend/projects/tienda` (clientes finales) y `frontend/projects/admin` (administración). El código común (llamadas a la API y WebSocket) está en `frontend/projects/shared`. |
| **Por qué ahí** | Son **aplicaciones distintas con usuarios distintos**: la tienda envía comandos y ve sus pedidos; el panel ve todo y despacha. Al ser independientes del servidor se despliegan y se actualizan por separado, y demuestran que varios clientes reciben el mismo broadcast. |

## 5. Flujo completo de un pedido

1. En la **Tienda**, el cliente pulsa "Hacer pedido" → `POST /api/comandos/pedidos`.
2. El servidor guarda el pedido y su evento `PEDIDO_CREADO` en el outbox (una transacción) y responde `202`.
3. `OutboxPublicador` publica el evento en **Kafka** (`pedidos.eventos`).
4. `ProyectorKafka` lo recibe y `ProyeccionServicio` actualiza el documento en la colección `pedido_vista` de **MongoDB**.
5. El servidor publica el evento en **RabbitMQ**: `ex.pedidos` (`pedido.creado` → `q.inventario`) y `ex.broadcast` (fanout).
6. `BroadcastWebSocket` recibe la copia del fanout y la envía por **WebSocket** a todos los clientes.
7. La Tienda y el Panel reciben la notificación y se actualizan solos.

## 6. Decisiones y límites

- **¿Por qué dos brokers?** Para mostrar los dos modelos vistos en clase (registro de eventos con Kafka, enrutamiento con RabbitMQ), cada uno en el trabajo que mejor hace. En un sistema real podría bastar uno.
- **Sin autenticación.** El nombre del cliente es solo un filtro. Está fuera del alcance de este trabajo.
- **Sin pagos reales.** "Pagar" solo cambia el estado.
- **Inventario, facturación y logística** solo escriben en el log lo que harían.
- **Los tests del servidor usan H2** en modo PostgreSQL (lado de comandos) y un almacén en memoria (lado de consultas), y no levantan Kafka, RabbitMQ ni MongoDB; el flujo completo con los brokers se prueba a mano (ver `docs/wiki/Pruebas.md`).

## 7. Criterios de aceptación

| Id | Prueba | Resultado esperado |
|---|---|---|
| CA-1 | Abrir Tienda en dos pestañas y Panel; crear un pedido en una | Aparece en la otra pestaña y en el Panel sin recargar. |
| CA-2 | Pagar el pedido | El log muestra `[FACTURACIÓN]`; todos los clientes ven `PAGADO`. |
| CA-3 | Enviar un pedido sin pagar (llamada directa a la API) | Error `409` con mensaje claro. |
| CA-4 | Apagar Kafka, crear un pedido, volver a encender Kafka | El pedido aparece al volver Kafka (outbox con reintentos). |
| CA-5 | Prueba automática `ProyeccionServicioTest.unEventoRepetidoSeIgnora` | Un evento repetido no cambia la vista (idempotencia). |
| CA-6 | Mirar `ex.broadcast` y `ex.pedidos` en el panel de RabbitMQ | Existen y tienen los enlaces de la sección 4. |
| CA-7 | Mirar las tablas en Supabase | Esquema `comando` con datos (`pedido`, `pedido_item`, `outbox_evento`). |
| CA-8 | Ejecutar `docker exec -it mongo mongosh tienda --eval "db.pedido_vista.find()"` | Los pedidos creados aparecen como documentos con sus ítems. |
