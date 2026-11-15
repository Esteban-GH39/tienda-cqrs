package co.vinni.tienda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * APLICACIÓN SERVIDORA. Un solo proceso con las dos mitades de CQRS separadas por paquetes:
 *  - co.vinni.tienda.comando   : escribe (POST /api/comandos/...) y guarda eventos en el outbox
 *  - co.vinni.tienda.consulta  : lee (GET /api/consultas/...) de un modelo propio, alimentado por Kafka
 *  - co.vinni.tienda.mensajeria: RabbitMQ (routing + broadcast) y WebSocket hacia los clientes
 */
@SpringBootApplication
@EnableScheduling
public class TiendaApplication {
    public static void main(String[] args) {
        SpringApplication.run(TiendaApplication.class, args);
    }
}
