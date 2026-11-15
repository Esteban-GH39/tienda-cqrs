package co.vinni.tienda.comando;

import jakarta.persistence.*;

import java.time.Instant;

/** Fila de la bandeja de salida: un evento esperando a ser publicado en Kafka. */
@Entity
@Table(name = "outbox_evento", schema = "comando")
public class OutboxEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private String eventId;
    private String clave;
    private String tipo;
    private String payload;

    @Column(name = "creado_en")
    private Instant creadoEn;
    private boolean publicado;

    protected OutboxEvento() {
    }

    public OutboxEvento(String eventId, String clave, String tipo, String payload) {
        this.eventId = eventId;
        this.clave = clave;
        this.tipo = tipo;
        this.payload = payload;
        this.creadoEn = Instant.now();
        this.publicado = false;
    }

    public void marcarPublicado() { this.publicado = true; }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getClave() { return clave; }
    public String getTipo() { return tipo; }
    public String getPayload() { return payload; }
    public boolean isPublicado() { return publicado; }
}
