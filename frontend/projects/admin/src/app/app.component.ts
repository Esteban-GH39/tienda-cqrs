import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '@shared/api.service';
import { TiempoRealService } from '@shared/tiempo-real.service';
import { Estado, PedidoVista, Resumen } from '@shared/models';

/**
 * CLIENTE 2 - PANEL DE ADMINISTRACIÓN. Otra aplicación, independiente de la tienda:
 * ve TODOS los pedidos, el resumen por estado, y despacha (enviar) o cancela.
 * Se actualiza sola con el BROADCAST: si alguien compra en la tienda, aquí aparece al instante.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, DatePipe],
  template: `
    <header class="barra">
      <h1>Panel de administración</h1>
      <span><span class="punto" [class.on]="tiempoReal.conectado()"></span>
        {{ tiempoReal.conectado() ? 'Conectado en vivo' : 'Sin conexión en vivo' }}</span>
    </header>

    <main class="contenedor">
      <section class="resumen">
        @for (e of estados; track e) {
          <div class="tarjeta dato">
            <span class="etiqueta" [class]="'etiqueta ' + e">{{ e }}</span>
            <strong>{{ resumen()[e] }}</strong>
          </div>
        }
      </section>

      <div class="grid2">
        <section class="tarjeta">
          <div class="fila" style="justify-content: space-between">
            <h2>Pedidos</h2>
            <select [ngModel]="filtro()" (ngModelChange)="cambiarFiltro($event)">
              <option value="">Todos los estados</option>
              @for (e of estados; track e) { <option [value]="e">{{ e }}</option> }
            </select>
          </div>
          @if (error()) { <p class="aviso error">{{ error() }}</p> }
          @if (pedidos().length === 0) {
            <p class="vacio">No hay pedidos para mostrar.</p>
          } @else {
            <table>
              <thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Estado</th><th>Acciones</th></tr></thead>
              <tbody>
                @for (p of pedidos(); track p.id) {
                  <tr>
                    <td><span class="mono">{{ p.id.slice(0, 8) }}</span><br><small>{{ p.creadoEn | date:'short' }}</small></td>
                    <td>{{ p.cliente }}</td>
                    <td>{{ p.total | currency:'USD':'symbol':'1.0-2' }}</td>
                    <td><span class="etiqueta" [class]="'etiqueta ' + p.estado">{{ p.estado }}</span></td>
                    <td><div class="fila">
                      @if (p.estado === 'CREADO') { <button class="suave" (click)="pagar(p)">Marcar pagado</button> }
                      @if (p.estado === 'PAGADO') { <button (click)="enviar(p)">Enviar</button> }
                      @if (p.estado === 'CREADO' || p.estado === 'PAGADO') {
                        <button class="peligro" (click)="cancelar(p)">Cancelar</button>
                      }
                    </div></td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </section>

        <section class="tarjeta">
          <h2>Eventos en vivo</h2>
          @if (tiempoReal.recientes().length === 0) { <p class="vacio">Esperando actividad…</p> }
          <ul class="feed">
            @for (n of tiempoReal.recientes(); track n.eventId) {
              <li>{{ n.tipo }}<small>{{ n.pedido?.cliente }} · {{ n.ocurridoEn | date:'mediumTime' }}</small></li>
            }
          </ul>
        </section>
      </div>
    </main>
  `,
  styles: [`
    .resumen { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; }
    @media (max-width: 700px) { .resumen { grid-template-columns: repeat(2, 1fr); } }
    .dato { display: flex; flex-direction: column; gap: .5rem; align-items: flex-start; }
    .dato strong { font-size: 2rem; }
  `],
})
export class AppComponent implements OnInit {
  private api = inject(ApiService);
  protected tiempoReal = inject(TiempoRealService);

  protected estados: Estado[] = ['CREADO', 'PAGADO', 'ENVIADO', 'CANCELADO'];
  protected pedidos = signal<PedidoVista[]>([]);
  protected resumen = signal<Resumen>({ CREADO: 0, PAGADO: 0, ENVIADO: 0, CANCELADO: 0 });
  protected filtro = signal('');
  protected error = signal('');

  ngOnInit(): void {
    this.tiempoReal.conectar();
    this.tiempoReal.eventos$.subscribe(n => {
      this.resumen.set(n.resumen);   // el broadcast ya trae el resumen actualizado
      this.cargarPedidos();
    });
    this.cargarPedidos();
    this.api.resumen().subscribe(r => this.resumen.set(r));
  }

  protected cambiarFiltro(estado: string): void {
    this.filtro.set(estado);
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
      error: () => this.error.set('No se pudo consultar el servidor. ¿Está encendido en el puerto 8080?'),
    });
  }
}
