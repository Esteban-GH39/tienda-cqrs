package co.vinni.tienda.comando;

public class PedidoNoEncontradoException extends RuntimeException {
    public PedidoNoEncontradoException(String id) {
        super("No existe el pedido " + id);
    }
}
