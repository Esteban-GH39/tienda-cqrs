package co.vinni.tienda.evento;

import java.math.BigDecimal;

/** Línea de un pedido dentro de un evento. */
public record ItemEvento(String producto, int cantidad, BigDecimal precioUnitario) {
}
