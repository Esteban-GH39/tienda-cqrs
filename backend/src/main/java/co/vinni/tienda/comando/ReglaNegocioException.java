package co.vinni.tienda.comando;

/** Un comando que rompe una regla del negocio (por ejemplo, enviar un pedido sin pagar). */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
