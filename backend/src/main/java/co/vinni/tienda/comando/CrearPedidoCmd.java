package co.vinni.tienda.comando;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/** COMANDO: la intención de crear un pedido. */
public record CrearPedidoCmd(
        @NotBlank @Size(max = 120) String cliente,
        @NotEmpty @Valid List<ItemCmd> items) {

    public record ItemCmd(
            @NotBlank @Size(max = 120) String producto,
            @Min(1) int cantidad,
            @NotNull @DecimalMin("0.01") BigDecimal precioUnitario) {
    }
}
