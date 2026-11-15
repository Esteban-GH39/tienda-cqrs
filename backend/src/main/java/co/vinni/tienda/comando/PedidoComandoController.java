package co.vinni.tienda.comando;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API de COMANDOS (solo escribe). Responde 202 Accepted: el comando se aceptó, pero el modelo de
 * lectura se actualiza un instante después, cuando el evento pasa por Kafka (consistencia eventual).
 */
@RestController
@RequestMapping("/api/comandos/pedidos")
public class PedidoComandoController {

    private final PedidoComandoServicio servicio;

    public PedidoComandoController(PedidoComandoServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody CrearPedidoCmd cmd) {
        String id = servicio.crear(cmd);
        return aceptado(id, EstadoPedido.CREADO);
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<Map<String, Object>> pagar(@PathVariable String id) {
        return aceptado(id, servicio.pagar(id));
    }

    @PostMapping("/{id}/enviar")
    public ResponseEntity<Map<String, Object>> enviar(@PathVariable String id) {
        return aceptado(id, servicio.enviar(id));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(@PathVariable String id) {
        return aceptado(id, servicio.cancelar(id));
    }

    private ResponseEntity<Map<String, Object>> aceptado(String id, EstadoPedido estado) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "pedidoId", id,
                "estado", estado.name(),
                "mensaje", "Comando aceptado. La consulta se actualiza cuando el evento se procese."));
    }
}
