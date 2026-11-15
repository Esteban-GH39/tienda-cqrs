package co.vinni.tienda.comando;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pedido_item", schema = "comando")
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String producto;
    private int cantidad;

    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;

    protected PedidoItem() {
    }

    public PedidoItem(String producto, int cantidad, BigDecimal precioUnitario) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public String getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }

    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}
