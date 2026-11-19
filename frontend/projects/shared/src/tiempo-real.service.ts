import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { WS_URL } from './config';
import { Notificacion } from './models';
import { TrazaService } from './traza.service';

/**
 * Conexión BROADCAST: se conecta por WebSocket (STOMP) y escucha dos destinos:
 *  - /topic/pedidos : cada cambio de un pedido (llega a TODOS los clientes conectados)
 *  - /topic/traza   : el log del stack en tiempo real (qué herramienta se usó y cuándo)
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService implements OnDestroy {
  private traza = inject(TrazaService);

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
        this.traza.local('Conexión WebSocket abierta', 'STOMP · suscrito a /topic/pedidos y /topic/traza');
        this.traza.cargarHistorial();
        this.cliente!.subscribe('/topic/pedidos', mensaje => {
          const n = JSON.parse(mensaje.body) as Notificacion;
          this.recientes.update(lista => [n, ...lista].slice(0, 15));
          this.traza.local('Broadcast recibido en este navegador', `${n.tipo} ← /topic/pedidos`, n.pedido?.id ?? null);
          this._eventos.next(n);
        });
        this.cliente!.subscribe('/topic/traza', mensaje => this.traza.recibido(JSON.parse(mensaje.body)));
      },
      onWebSocketClose: () => {
        if (this.conectado()) this.traza.local('Conexión WebSocket cerrada', 'Se reintenta cada 3 segundos');
        this.conectado.set(false);
      },
      onStompError: () => this.conectado.set(false),
    });
    this.cliente.activate();
  }

  ngOnDestroy(): void {
    this.cliente?.deactivate();
  }
}
