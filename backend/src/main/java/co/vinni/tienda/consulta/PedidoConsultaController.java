package co.vinni.tienda.consulta;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** API de CONSULTAS (solo lee del modelo de lectura; nunca toca las tablas de comando). */
@RestController
@RequestMapping("/api/consultas")
public class PedidoConsultaController {

    private final PedidoVistaRepositorio vistas;

    public PedidoConsultaController(PedidoVistaRepositorio vistas) {
        this.vistas = vistas;
    }

    @GetMapping("/pedidos")
    public List<PedidoVista> listar(@RequestParam(required = false) String estado,
                                    @RequestParam(required = false) String cliente) {
        return vistas.listar(estado, cliente);
    }

    @GetMapping("/pedidos/{id}")
    public ResponseEntity<PedidoVista> uno(@PathVariable String id) {
        return vistas.buscar(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/resumen")
    public Map<String, Long> resumen() {
        return vistas.resumen();
    }
}
