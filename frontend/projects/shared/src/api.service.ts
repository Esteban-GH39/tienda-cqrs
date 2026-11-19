import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API } from './config';
import { ItemPedido, PedidoVista, Resumen } from './models';
import { TrazaService } from './traza.service';

/** Habla con el servidor: COMANDOS (POST, escriben) y CONSULTAS (GET, leen). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private traza = inject(TrazaService);

  // ---------- comandos ----------
  crearPedido(cliente: string, items: ItemPedido[]): Observable<{ pedidoId: string }> {
    this.traza.local('Comando enviado: crear pedido', 'POST /api/comandos/pedidos');
    return this.http.post<{ pedidoId: string }>(`${API}/api/comandos/pedidos`, { cliente, items });
  }
  pagar(id: string) { return this.comando(id, 'pagar'); }
  enviar(id: string) { return this.comando(id, 'enviar'); }
  cancelar(id: string) { return this.comando(id, 'cancelar'); }

  private comando(id: string, accion: 'pagar' | 'enviar' | 'cancelar') {
    this.traza.local(`Comando enviado: ${accion} pedido`, `POST /api/comandos/pedidos/{id}/${accion}`, id);
    return this.http.post(`${API}/api/comandos/pedidos/${id}/${accion}`, {});
  }

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
