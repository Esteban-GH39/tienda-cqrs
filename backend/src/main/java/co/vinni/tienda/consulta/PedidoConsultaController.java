package co.vinni.tienda.consulta;

import co.vinni.tienda.traza.TrazaServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** API de CONSULTAS (solo lee del modelo de lectura; nunca toca las tablas de comando). */
@RestController
@RequestMapping("/api/consultas")
public class PedidoConsultaController {

    private final PedidoVistaRepositorio vistas;
    private final TrazaServicio traza;

    public PedidoConsultaController(PedidoVistaRepositorio vistas, TrazaServicio traza) {
        this.vistas = vistas;
        this.traza = traza;
    }

    @GetMapping("/pedidos")
    public List<PedidoVista> listar(@RequestParam(required = false) String estado,
                                    @RequestParam(required = false) String cliente) {
        List<PedidoVista> lista = vistas.listar(estado, cliente);
        traza.registrarLimitado("consulta-pedidos", 1500, TrazaServicio.MONGODB, "Consulta leída de MongoDB",
                "GET /api/consultas/pedidos → pedido_vista (" + lista.size() + " documento(s))", null);
        return lista;
    }

    @GetMapping("/pedidos/{id}")
    public ResponseEntity<PedidoVista> uno(@PathVariable String id) {
        return vistas.buscar(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/resumen")
    public Map<String, Long> resumen() {
        Map<String, Long> conteo = vistas.resumen();
        traza.registrarLimitado("consulta-resumen", 1500, TrazaServicio.MONGODB, "Resumen calculado en MongoDB",
                "GET /api/consultas/resumen → pedido_vista agrupado por estado", null);
        return conteo;
    }
}
