import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { Herramienta, LineaTraza, TrazaService } from './traza.service';

interface Nodo { clave: Herramienta; nombre: string; rol: string; color: string; ayuda: string; }

/**
 * Las herramientas en el MISMO ORDEN del diagrama de arquitectura del README:
 * navegador → servidor → comandos → eventos → consultas → colas → broadcast.
 */
const NODOS: Nodo[] = [
  { clave: 'ANGULAR',   nombre: 'Angular',    rol: 'Navegador', color: '#DD0031',
    ayuda: 'La Tienda y el Panel: envían comandos y reciben el broadcast.' },
  { clave: 'SPRING',    nombre: 'Spring Boot', rol: 'Servidor', color: '#7AB800',
    ayuda: 'Recibe los comandos y las consultas y coordina todo lo demás.' },
  { clave: 'POSTGRES',  nombre: 'PostgreSQL', rol: 'Comandos', color: '#336791',
    ayuda: 'Base de datos que ESCRIBE: pedidos y outbox (en Supabase).' },
  { clave: 'KAFKA',     nombre: 'Kafka',      rol: 'Eventos', color: '#2B2F3A',
    ayuda: 'Lleva los eventos, en orden, del lado de comandos al de consultas.' },
  { clave: 'MONGODB',   nombre: 'MongoDB',    rol: 'Consultas', color: '#0E9F6E',
    ayuda: 'Base de datos que LEE: los pedidos ya listos para mostrar.' },
  { clave: 'RABBITMQ',  nombre: 'RabbitMQ',   rol: 'Colas', color: '#F26522',
    ayuda: 'Enruta cada evento a su cola (inventario, facturación, logística) y hace el broadcast.' },
  { clave: 'WEBSOCKET', nombre: 'WebSocket',  rol: 'Broadcast', color: '#7C3AED',
    ayuda: 'Empuja cada cambio a todos los navegadores conectados, sin que pregunten.' },
];

/** Pestaña "Log del stack": muestra en tiempo real cuándo se usa cada herramienta. */
@Component({
  selector: 'app-log-stack',
  standalone: true,
  imports: [DatePipe],
  template: `
    <section class="log">
      <div class="log-intro">
        <h2>Log del stack en vivo</h2>
      </div>

      <div class="pila" role="list" aria-label="Herramientas del stack">
        @for (n of nodos; track n.clave; let ultimo = $last) {
          <div class="nodo" role="listitem" [class.encendido]="encendidos()[n.clave]"
               [style.--c]="n.color" [title]="n.ayuda">
            <span class="nodo-nombre">{{ n.nombre }}</span>
            <span class="nodo-rol">{{ n.rol }}</span>
            <span class="nodo-usos" aria-label="veces usado">{{ usos()[n.clave] }}</span>
          </div>
          @if (!ultimo) { <span class="cable" aria-hidden="true"></span> }
        }
      </div>

      <div class="log-barra">
        <div class="filtros" role="group" aria-label="Filtrar por herramienta">
          @for (n of nodos; track n.clave) {
            <button type="button" class="filtro" [class.activo]="filtradaPor(n.clave)"
                    [style.--c]="n.color" [attr.aria-pressed]="filtradaPor(n.clave)"
                    (click)="alternar(n.clave)">{{ n.nombre }}</button>
          }
        </div>
        <div class="acciones">
          @if (filtroPedido()) {
            <button type="button" class="chip" (click)="filtroPedido.set(null)">
              Pedido {{ corto(filtroPedido()!) }} (quitar filtro)
            </button>
          }
          <button type="button" class="secundario" (click)="alternarPausa()">
            {{ pausado() ? 'Reanudar' : 'Pausar' }}
          </button>
          <button type="button" class="secundario" (click)="traza.limpiar()">Limpiar</button>
        </div>
      </div>

      @if (pausado()) {
        <p class="aviso">
          Log en pausa. @if (pendientes() > 0) { Llegaron {{ pendientes() }} líneas nuevas. }
        </p>
      }

      @if (visibles().length === 0) {
        <p class="vacio">
          {{ traza.lineas().length === 0
              ? 'Todavía no hay actividad. Haz un pedido en la Tienda y aquí verás cada paso.'
              : 'Ninguna línea coincide con los filtros.' }}
        </p>
      } @else {
        <ol class="lineas">
          @for (l of visibles(); track l.id) {
            <li class="linea" [class.nueva]="esNueva(l)" [style.--c]="meta(l.herramienta).color">
              <time class="hora" [attr.datetime]="l.ts">{{ l.ts | date:'HH:mm:ss.SSS' }}</time>
              <span class="herr">{{ meta(l.herramienta).nombre }}</span>
              <div class="texto">
                <strong>{{ l.accion }}</strong>
                <span class="detalle">{{ l.detalle }}</span>
              </div>
              @if (l.pedidoId) {
                <button type="button" class="pedido-id" (click)="filtroPedido.set(l.pedidoId)"
                        title="Ver solo este pedido">{{ corto(l.pedidoId) }}</button>
              }
            </li>
          }
        </ol>
      }
    </section>
  `,
})
export class LogStackComponent implements OnInit, OnDestroy {
  protected traza = inject(TrazaService);
  protected nodos = NODOS;

  protected filtros = signal<Set<Herramienta>>(new Set());
  protected filtroPedido = signal<string | null>(null);
  protected pausado = signal(false);
  protected encendidos = signal<Record<string, boolean>>({});

  private congeladas = signal<LineaTraza[]>([]);
  private totalAlPausar = 0;
  private sub?: Subscription;
  private apagadores = new Map<string, ReturnType<typeof setTimeout>>();

  protected visibles = computed(() => {
    const base = this.pausado() ? this.congeladas() : this.traza.lineas();
    const herramientas = this.filtros();
    const pedido = this.filtroPedido();
    return base.filter(l =>
      (herramientas.size === 0 || herramientas.has(l.herramienta)) &&
      (!pedido || l.pedidoId === pedido));
  });

  protected usos = computed(() => {
    const cuenta: Record<string, number> = {};
    for (const n of NODOS) cuenta[n.clave] = 0;
    for (const l of this.traza.lineas()) cuenta[l.herramienta] = (cuenta[l.herramienta] ?? 0) + 1;
    return cuenta;
  });

  protected pendientes = computed(() => this.traza.total() - this.totalAlPausar);

  ngOnInit(): void {
    this.traza.marcarLeido();
    this.traza.cargarHistorial();
    this.sub = this.traza.nueva$.subscribe(l => {
      this.traza.marcarLeido();
      this.encender(l.herramienta);
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.apagadores.forEach(t => clearTimeout(t));
  }

  protected meta(clave: Herramienta): Nodo {
    return NODOS.find(n => n.clave === clave) ?? NODOS[0];
  }

  protected filtradaPor(clave: Herramienta): boolean { return this.filtros().has(clave); }

  protected alternar(clave: Herramienta): void {
    this.filtros.update(actual => {
      const copia = new Set(actual);
      if (copia.has(clave)) copia.delete(clave); else copia.add(clave);
      return copia;
    });
  }

  protected alternarPausa(): void {
    if (this.pausado()) {
      this.pausado.set(false);
    } else {
      this.congeladas.set(this.traza.lineas());
      this.totalAlPausar = this.traza.total();
      this.pausado.set(true);
    }
  }

  protected corto(id: string): string { return id.slice(0, 8); }

  protected esNueva(l: LineaTraza): boolean { return l.llegada > 0 && Date.now() - l.llegada < 1500; }

  /** Ilumina la herramienta en la franja durante un instante. */
  private encender(clave: string): void {
    this.encendidos.update(e => ({ ...e, [clave]: true }));
    clearTimeout(this.apagadores.get(clave));
    this.apagadores.set(clave, setTimeout(() => {
      this.encendidos.update(e => ({ ...e, [clave]: false }));
    }, 1200));
  }
}
