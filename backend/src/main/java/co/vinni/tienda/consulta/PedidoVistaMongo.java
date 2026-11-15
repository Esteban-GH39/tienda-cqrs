package co.vinni.tienda.consulta;

import co.vinni.tienda.comando.EstadoPedido;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Implementación del lado de LECTURA con MongoDB (base "tienda"). */
@Repository
public class PedidoVistaMongo implements PedidoVistaRepositorio {

    private static final String EVENTOS_APLICADOS = "evento_aplicado";

    private final MongoTemplate mongo;

    public PedidoVistaMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<PedidoVista> listar(String estado, String cliente) {
        Query q = new Query();
        if (estado != null && !estado.isBlank()) {
            q.addCriteria(Criteria.where("estado").is(estado.toUpperCase()));
        }
        if (cliente != null && !cliente.isBlank()) {
            // igual sin importar mayúsculas
            q.addCriteria(Criteria.where("cliente").regex("^" + Pattern.quote(cliente.trim()) + "$", "i"));
        }
        q.with(Sort.by(Sort.Direction.DESC, "creadoEn")).limit(200);
        return mongo.find(q, PedidoVista.class);
    }

    @Override
    public Optional<PedidoVista> buscar(String id) {
        return Optional.ofNullable(mongo.findById(id, PedidoVista.class));
    }

    @Override
    public Map<String, Long> resumen() {
        Map<String, Long> r = new LinkedHashMap<>();
        for (EstadoPedido e : EstadoPedido.values()) {
            r.put(e.name(), mongo.count(Query.query(Criteria.where("estado").is(e.name())), PedidoVista.class));
        }
        return r;
    }

    @Override
    public boolean eventoYaAplicado(String eventId) {
        return mongo.exists(Query.query(Criteria.where("_id").is(eventId)), EVENTOS_APLICADOS);
    }

    @Override
    public void marcarEventoAplicado(String eventId) {
        try {
            mongo.insert(new Document("_id", eventId).append("aplicadoEn", new Date()), EVENTOS_APLICADOS);
        } catch (DuplicateKeyException ignorar) {
            // ya estaba marcado
        }
    }

    @Override
    public void insertarSiNoExiste(PedidoVista vista) {
        try {
            mongo.insert(vista);
        } catch (DuplicateKeyException ignorar) {
            // ya existía: no se pisa
        }
    }

    @Override
    public boolean actualizarEstado(String id, String estado, Instant cuando) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
                new Update().set("estado", estado).set("actualizadoEn", cuando),
                PedidoVista.class).getMatchedCount() > 0;
    }
}
