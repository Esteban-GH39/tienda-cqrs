import { Estado, Notificacion } from './models';

/** Textos y pasos para mostrar los pedidos (compartido por la Tienda y el Panel). */
export const ESTADO_TEXTO: Record<Estado, string> = {
  CREADO: 'Creado', PAGADO: 'Pagado', ENVIADO: 'Enviado', CANCELADO: 'Cancelado',
};

/** El pedido avanza CREADO → PAGADO → ENVIADO. Devuelve cuántos pasos lleva (0 si se canceló). */
export function pasosCumplidos(estado: Estado): number {
  return { CREADO: 1, PAGADO: 2, ENVIADO: 3, CANCELADO: 0 }[estado];
}

export const RUTA_PEDIDO: { clave: Estado; texto: string }[] = [
  { clave: 'CREADO', texto: 'Creado' },
  { clave: 'PAGADO', texto: 'Pagado' },
  { clave: 'ENVIADO', texto: 'Enviado' },
];

/** Frase corta para la lista "en vivo". */
export function frase(n: Notificacion): string {
  const quien = n.pedido?.cliente ?? 'Alguien';
  switch (n.tipo) {
    case 'PEDIDO_CREADO': return `${quien} hizo un pedido`;
    case 'PEDIDO_PAGADO': return `Pedido de ${quien} pagado`;
    case 'PEDIDO_ENVIADO': return `Pedido de ${quien} enviado`;
    case 'PEDIDO_CANCELADO': return `Pedido de ${quien} cancelado`;
    default: return n.tipo;
  }
}
