import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ApiService } from '@shared/api.service';
import { TiempoRealService } from '@shared/tiempo-real.service';
import { TrazaService } from '@shared/traza.service';
import { LogStackComponent } from '@shared/log-stack.component';
import { Estado, PedidoVista, Resumen } from '@shared/models';
import { ESTADO_TEXTO, frase } from '@shared/presentacion';

type Vista = 'pedidos' | 'log';

/**
 * CLIENTE 2 - PANEL DE ADMINISTRACIÓN. Otra aplicación, independiente de la tienda:
 * ve TODOS los pedidos, el resumen por estado, y despacha (enviar) o cancela.
 * Se actualiza sola con el BROADCAST: si alguien compra en la tienda, aquí aparece al instante.
 * También tiene la pestaña "Log del stack".
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, LogStackComponent],
  template: `
    <header class="top">
      <div class="marca">Tienda en vivo <small>Panel de administración</small></div>
      <nav class="tabs" role="tablist" aria-label="Secciones">
        <button type="button" role="tab" [attr.aria-selected]="vista() === 'pedidos'" (click)="ir('pedidos')">Pedidos</button>
        <button type="button" role="tab" [attr.aria-selected]="vista() === 'log'" (click)="ir('log')">
          Log del stack
          @if (vista() !== 'log' && traza.sinLeer() > 0) { <span class="insignia viva">{{ traza.sinLeer() > 99 ? '99+' : traza.sinLeer() }}</span> }
        </button>
      </nav>
      <span class="conexion"><span class="punto" [class.on]="tiempoReal.conectado()"></span>
        {{ tiempoReal.conectado() ? 'Conectado en vivo' : 'Sin conexión en vivo' }}</span>
    </header>

    <main class="pagina">
      @switch (vista()) {
        @case ('pedidos') {
          <div class="embudo" role="group" aria-label="Filtrar pedidos por estado">
            <button type="button" class="etapa todos" [attr.aria-pressed]="filtro() === ''" (click)="cambiarFiltro('')">
              <strong>{{ totalPedidos() }}</strong><span>Todos</span>
            </button>
            @for (e of etapas; track e) {
              <button type="button" class="etapa" [style.--c]="'var(--' + e.toLowerCase() + ')'"
                      [attr.aria-pressed]="filtro() === e" (click)="cambiarFiltro(e)">
                <strong>{{ resumen()[e] }}</strong><span>{{ texto(e) }}</span>
              </button>
            }
            <button type="button" class="etapa fin" [style.--c]="'var(--cancelado)'"
                    [attr.aria-pressed]="filtro() === 'CANCELADO'" (click)="cambiarFiltro('CANCELADO')">
              <strong>{{ resumen()['CANCELADO'] }}</strong><span>Cancelado</span>
            </button>
          </div>

          <div class="dos-col">
            <section>
              <div class="encabezado">
                <h2>{{ filtro() ? 'Pedidos ' + texto(filtro()).toLowerCase() + 's' : 'Todos los pedidos' }}</h2>
              </div>
              @if (error()) { <p class="aviso error">{{ error() }}</p> }
              @if (pedidos().length === 0) {
                <p class="vacio">No hay pedidos para mostrar.</p>
              } @else {
                <div class="envoltura-tabla">
                  <table class="tabla">
                    <thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Estado</th><th>Acciones</th></tr></thead>
                    <tbody>
                      @for (p of pedidos(); track p.id) {
                        <tr>
                          <td><span class="mono">{{ p.id.slice(0, 8) }}</span>
                            <div class="suave" style="font-size:.8rem">{{ p.creadoEn | date:'d MMM, h:mm a' }}</div></td>
                          <td>{{ p.cliente }}</td>
                          <td class="total-fila">{{ p.total | currency:'USD':'symbol':'1.0-2' }}</td>
                          <td><span class="chip-estado" [style.--c]="'var(--' + p.estado.toLowerCase() + ')'">{{ texto(p.estado) }}</span></td>
                          <td><div class="acciones-fila">
                            @if (p.estado === 'CREADO') { <button type="button" class="secundario" (click)="pagar(p)">Marcar pagado</button> }
                            @if (p.estado === 'PAGADO') { <button type="button" (click)="enviar(p)">Enviar</button> }
                            @if (p.estado === 'CREADO' || p.estado === 'PAGADO') {
                              <button type="button" class="peligro" (click)="cancelar(p)">Cancelar</button>
                            }
                          </div></td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </section>

            <aside class="vivo">
              <h2>Eventos en vivo</h2>
              @if (tiempoReal.recientes().length === 0) { <p class="vacio">Esperando actividad…</p> }
              <ul>
                @for (n of tiempoReal.recientes(); track n.eventId) {
                  <li><span>{{ fraseDe(n) }}</span><small>{{ n.ocurridoEn | date:'mediumTime' }}</small></li>
                }
              </ul>
            </aside>
          </div>
        }
        @case ('log') { <app-log-stack /> }
      }
    </main>
  `,
})
export class AppComponent implements OnInit {
  private api = inject(ApiService);
  protected tiempoReal = inject(TiempoRealService);
  protected traza = inject(TrazaService);

  protected etapas: Estado[] = ['CREADO', 'PAGADO', 'ENVIADO'];
  protected vista = signal<Vista>('pedidos');
  protected pedidos = signal<PedidoVista[]>([]);
  protected resumen = signal<Resumen>({ CREADO: 0, PAGADO: 0, ENVIADO: 0, CANCELADO: 0 });
  protected filtro = signal<Estado | ''>('');
  protected error = signal('');

  protected fraseDe = frase;

  ngOnInit(): void {
    document.body.dataset['app'] = 'admin';
    this.tiempoReal.conectar();
    this.tiempoReal.eventos$.subscribe(n => {
      this.resumen.set(n.resumen);   // el broadcast ya trae el resumen actualizado
      this.cargarPedidos();
    });
    this.cargarPedidos();
    this.api.resumen().subscribe({ next: r => this.resumen.set(r), error: () => { /* lo muestra cargarPedidos */ } });
  }

  protected ir(v: Vista): void {
    this.vista.set(v);
    if (v === 'log') this.traza.marcarLeido();
  }

  protected totalPedidos(): number {
    const r = this.resumen();
    return r.CREADO + r.PAGADO + r.ENVIADO + r.CANCELADO;
  }

  protected texto(e: Estado | ''): string { return e ? ESTADO_TEXTO[e] : ''; }

  protected cambiarFiltro(estado: Estado | ''): void {
    this.filtro.set(this.filtro() === estado ? '' : estado);
    this.cargarPedidos();
  }

  protected pagar(p: PedidoVista): void { this.accion(this.api.pagar(p.id)); }
  protected enviar(p: PedidoVista): void { this.accion(this.api.enviar(p.id)); }
  protected cancelar(p: PedidoVista): void { this.accion(this.api.cancelar(p.id)); }

  private accion(llamada: ReturnType<ApiService['pagar']>): void {
    this.error.set('');
    llamada.subscribe({ error: (e: any) => this.error.set(e?.error?.error ?? 'No se pudo completar la operación.') });
  }

  private cargarPedidos(): void {
    this.api.listar({ estado: this.filtro() }).subscribe({
      next: lista => { this.pedidos.set(lista); this.error.set(''); },
      error: () => this.error.set('No se pudo consultar el servidor. ¿Está encendido?'),
    });
  }
}
