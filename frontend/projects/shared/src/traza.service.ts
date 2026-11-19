import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { API } from './config';

export type Herramienta = 'ANGULAR' | 'SPRING' | 'POSTGRES' | 'KAFKA' | 'MONGODB' | 'RABBITMQ' | 'WEBSOCKET';

/** Una línea del log: "en este momento se usó esta herramienta para esto". */
export interface LineaTraza {
  id: string;
  seq: number | null;          // lo asigna el servidor; las líneas del navegador no tienen
  ts: string;                  // ISO
  herramienta: Herramienta;
  accion: string;
  detalle: string;
  pedidoId: string | null;
  llegada: number;             // hora local en que llegó a este navegador (para resaltar lo nuevo)
}

interface TrazaServidor {
  seq: number; ts: string; herramienta: string; accion: string; detalle: string; pedidoId: string | null;
}

const MAXIMO = 400;

/**
 * LOG DEL STACK. Junta dos fuentes:
 *  - las líneas que el SERVIDOR emite por WebSocket (/topic/traza): Spring, PostgreSQL, Kafka, MongoDB, RabbitMQ, WebSocket
 *  - las líneas de ESTE NAVEGADOR (Angular): comandos enviados y broadcast recibido
 */
@Injectable({ providedIn: 'root' })
export class TrazaService {
  private http = inject(HttpClient);

  readonly lineas = signal<LineaTraza[]>([]);   // la más nueva primero
  readonly total = signal(0);                   // cuántas han llegado desde que se abrió la página
  readonly sinLeer = signal(0);                 // las que llegaron mientras no se miraba la pestaña Log

  private readonly _nueva = new Subject<LineaTraza>();
  readonly nueva$ = this._nueva.asObservable();

  private vistos = new Set<number>();
  private historialCargado = false;
  private contadorLocal = 0;

  /** Línea que llegó del servidor por WebSocket. */
  recibido(r: TrazaServidor): void {
    if (this.vistos.has(r.seq)) return;
    this.vistos.add(r.seq);
    this.agregar(this.convertir(r, Date.now()));
  }

  /** Línea propia de este navegador (Angular). */
  local(accion: string, detalle: string, pedidoId: string | null = null): void {
    this.agregar({
      id: `local-${++this.contadorLocal}`, seq: null, ts: new Date().toISOString(),
      herramienta: 'ANGULAR', accion, detalle, pedidoId, llegada: Date.now(),
    });
  }

  /** Trae lo que pasó antes de abrir la página (el servidor guarda las últimas 300 líneas). */
  cargarHistorial(): void {
    if (this.historialCargado) return;
    this.historialCargado = true;
    this.http.get<TrazaServidor[]>(`${API}/api/traza`).subscribe({
      next: filas => {
        const nuevas = filas.filter(f => !this.vistos.has(f.seq));
        nuevas.forEach(f => this.vistos.add(f.seq));
        const antiguas = nuevas.map(f => this.convertir(f, 0)).reverse();
        this.lineas.update(actual => [...actual, ...antiguas].slice(0, MAXIMO));
      },
      error: () => { this.historialCargado = false; },
    });
  }

  limpiar(): void { this.lineas.set([]); }
  marcarLeido(): void { this.sinLeer.set(0); }

  private agregar(l: LineaTraza): void {
    this.lineas.update(actual => [l, ...actual].slice(0, MAXIMO));
    this.total.update(n => n + 1);
    this.sinLeer.update(n => n + 1);
    this._nueva.next(l);
  }

  private convertir(r: TrazaServidor, llegada: number): LineaTraza {
    return {
      id: `srv-${r.seq}`, seq: r.seq, ts: r.ts, herramienta: r.herramienta as Herramienta,
      accion: r.accion, detalle: r.detalle, pedidoId: r.pedidoId, llegada,
    };
  }
}
