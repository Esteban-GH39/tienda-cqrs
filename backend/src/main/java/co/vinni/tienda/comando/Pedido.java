package co.vinni.tienda.comando;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Modelo de ESCRITURA. Aquí viven las reglas del negocio (qué transiciones de estado se permiten).
 * No se usa para leer: las pantallas consultan el modelo de lectura (schema "consulta").
 */
@Entity
@Table(name = "pedido", schema = "comando")
public class Pedido {

    @Id
    private String id;
    private String cliente;
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;

    @Column(name = "creado_en")
    private Instant creadoEn;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "pedido_id", nullable = false)
    private List<PedidoItem> items = new ArrayList<>();

    protected Pedido() {
    }

    public static Pedido crear(String cliente, List<PedidoItem> items) {
        Pedido p = new Pedido();
        p.id = UUID.randomUUID().toString();
        p.cliente = cliente;
        p.items.addAll(items);
        p.total = items.stream().map(PedidoItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        p.estado = EstadoPedido.CREADO;
        p.creadoEn = Instant.now();
        p.actualizadoEn = p.creadoEn;
        return p;
    }

    public void pagar() {
        exigir(estado == EstadoPedido.CREADO, "Solo se puede pagar un pedido en estado CREADO (estado actual: " + estado + ")");
        cambiarA(EstadoPedido.PAGADO);
    }

    public void enviar() {
        exigir(estado == EstadoPedido.PAGADO, "Solo se puede enviar un pedido PAGADO (estado actual: " + estado + ")");
        cambiarA(EstadoPedido.ENVIADO);
    }

    public void cancelar() {
        exigir(estado == EstadoPedido.CREADO || estado == EstadoPedido.PAGADO,
                "Solo se puede cancelar un pedido CREADO o PAGADO (estado actual: " + estado + ")");
        cambiarA(EstadoPedido.CANCELADO);
    }

    private void cambiarA(EstadoPedido nuevo) {
        this.estado = nuevo;
        this.actualizadoEn = Instant.now();
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new ReglaNegocioException(mensaje);
        }
    }

    public String getId() { return id; }
    public String getCliente() { return cliente; }
    public BigDecimal getTotal() { return total; }
    public EstadoPedido getEstado() { return estado; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public List<PedidoItem> getItems() { return items; }
}
