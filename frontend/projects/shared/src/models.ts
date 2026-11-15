export type Estado = 'CREADO' | 'PAGADO' | 'ENVIADO' | 'CANCELADO';

export interface ItemPedido {
  producto: string;
  cantidad: number;
  precioUnitario: number;
}

/** Fila del modelo de LECTURA (lo que devuelve GET /api/consultas/pedidos). */
export interface PedidoVista {
  id: string;
  cliente: string;
  total: number;
  estado: Estado;
  items: ItemPedido[];
  creadoEn: string;
  actualizadoEn: string;
}

export type Resumen = Record<Estado, number>;

/** Mensaje de BROADCAST que llega por WebSocket a todos los clientes. */
export interface Notificacion {
  tipo: string;            // PEDIDO_CREADO, PEDIDO_PAGADO, ...
  eventId: string;
  ocurridoEn: string;
  pedido: PedidoVista | null;
  resumen: Resumen;
}
