import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API } from './config';
import { ItemPedido, PedidoVista, Resumen } from './models';

/** Habla con el servidor: COMANDOS (POST, escriben) y CONSULTAS (GET, leen). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // ---------- comandos ----------
  crearPedido(cliente: string, items: ItemPedido[]): Observable<{ pedidoId: string }> {
    return this.http.post<{ pedidoId: string }>(`${API}/api/comandos/pedidos`, { cliente, items });
  }
  pagar(id: string) { return this.http.post(`${API}/api/comandos/pedidos/${id}/pagar`, {}); }
  enviar(id: string) { return this.http.post(`${API}/api/comandos/pedidos/${id}/enviar`, {}); }
  cancelar(id: string) { return this.http.post(`${API}/api/comandos/pedidos/${id}/cancelar`, {}); }

  // ---------- consultas ----------
  listar(filtros: { estado?: string; cliente?: string } = {}): Observable<PedidoVista[]> {
    const q = new URLSearchParams();
    if (filtros.estado) q.set('estado', filtros.estado);
    if (filtros.cliente) q.set('cliente', filtros.cliente);
    const sufijo = q.toString() ? `?${q}` : '';
    return this.http.get<PedidoVista[]>(`${API}/api/consultas/pedidos${sufijo}`);
  }
  resumen(): Observable<Resumen> {
    return this.http.get<Resumen>(`${API}/api/consultas/resumen`);
  }
}
