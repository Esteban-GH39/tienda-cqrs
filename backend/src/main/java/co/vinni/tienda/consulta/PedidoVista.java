package co.vinni.tienda.consulta;

import co.vinni.tienda.evento.ItemEvento;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Modelo de LECTURA: un DOCUMENTO de MongoDB (colección "pedido_vista") con el pedido y sus ítems juntos,
 * listo para mostrar. No hay joins: una sola lectura trae todo lo que necesita la pantalla.
 */
@Document(collection = "pedido_vista")
public record PedidoVista(@Id String id, String cliente, BigDecimal total, String estado,
                          List<ItemEvento> items, Instant creadoEn, Instant actualizadoEn) {
}
