package co.vinni.tienda.traza;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Historial reciente del log del stack (para llenar la pestaña "Log" al abrirla). */
@RestController
@RequestMapping("/api/traza")
public class TrazaController {

    private final TrazaServicio traza;

    public TrazaController(TrazaServicio traza) {
        this.traza = traza;
    }

    @GetMapping
    public List<TrazaEvento> recientes() {
        return traza.recientes();
    }
}
