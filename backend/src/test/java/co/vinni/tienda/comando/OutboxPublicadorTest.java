package co.vinni.tienda.comando;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OutboxPublicador.class)
class OutboxPublicadorTest {

    @Autowired OutboxPublicador publicador;
    @Autowired OutboxRepositorio outbox;
    @MockBean KafkaTemplate<String, String> kafka;

    @Test
    void publicaEnOrdenYMarcaComoPublicado() {
        outbox.save(new OutboxEvento("e1", "p1", "PEDIDO_CREADO", "{}"));
        outbox.save(new OutboxEvento("e2", "p1", "PEDIDO_PAGADO", "{}"));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publicador.publicarPendientes();

        verify(kafka, times(2)).send(eq("pedidos.eventos"), eq("p1"), anyString());
        assertEquals(0, outbox.findTop100ByPublicadoFalseOrderByIdAsc().size());
    }

    @Test
    void siKafkaFallaElEventoQuedaPendiente() {
        outbox.save(new OutboxEvento("e1", "p1", "PEDIDO_CREADO", "{}"));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka caído")));

        publicador.publicarPendientes();

        assertEquals(1, outbox.findTop100ByPublicadoFalseOrderByIdAsc().size());
    }
}
