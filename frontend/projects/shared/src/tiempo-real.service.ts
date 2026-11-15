import { Injectable, OnDestroy, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { WS_URL } from './config';
import { Notificacion } from './models';

/**
 * Conexión BROADCAST: se conecta por WebSocket (STOMP) y escucha /topic/pedidos.
 * El servidor envía ahí cada cambio de un pedido y le llega a TODOS los clientes conectados.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService implements OnDestroy {
  readonly conectado = signal(false);
  readonly recientes = signal<Notificacion[]>([]);
  private readonly _eventos = new Subject<Notificacion>();
  readonly eventos$ = this._eventos.asObservable();

  private cliente?: Client;

  conectar(): void {
    if (this.cliente) return;
    this.cliente = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,            // si se cae el servidor, reintenta solo
      onConnect: () => {
        this.conectado.set(true);
        this.cliente!.subscribe('/topic/pedidos', mensaje => {
          const n = JSON.parse(mensaje.body) as Notificacion;
          this.recientes.update(lista => [n, ...lista].slice(0, 15));
          this._eventos.next(n);
        });
      },
      onWebSocketClose: () => this.conectado.set(false),
      onStompError: () => this.conectado.set(false),
    });
    this.cliente.activate();
  }

  ngOnDestroy(): void {
    this.cliente?.deactivate();
  }
}
