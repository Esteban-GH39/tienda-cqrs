import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '@shared/api.service';
import { TiempoRealService } from '@shared/tiempo-real.service';
import { PedidoVista } from '@shared/models';

interface Producto { nombre: string; precio: number; }

/**
 * CLIENTE 1 - TIENDA. Aplicación independiente del servidor:
 *  - envía COMANDOS (crear / pagar / cancelar pedido)
 *  - lee CONSULTAS (mis pedidos)
 *  - escucha el BROADCAST por WebSocket para actualizarse sin recargar.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, DatePipe],
  template: `
    <header class="barra">
      <h1>Tienda en vivo</h1>
      <span><span class="punto" [class.on]="tiempoReal.conectado()"></span>
        {{ tiempoReal.conectado() ? 'Conectado en vivo' : 'Sin conexión en vivo' }}</span>
    </header>

    <main class="contenedor">
      <section class="tarjeta">
        <h2>Tu nombre</h2>
        <div class="fila">
          <input placeholder="Ej: Ana Gómez" [ngModel]="cliente()" (ngModelChange)="cambiarCliente($event)" />
          <span class="mono">Se usa para filtrar "Mis pedidos".</span>
        </div>
      </section>

      <div class="grid2">
        <section class="tarjeta">
          <h2>Catálogo</h2>
          <table>
            <thead><tr><th>Producto</th><th>Precio</th><th></th></tr></thead>
            <tbody>
              @for (p of catalogo; track p.nombre) {
                <tr>
                  <td>{{ p.nombre }}</td>
                  <td>{{ p.precio | currency:'USD':'symbol':'1.0-2' }}</td>
                  <td><button class="suave" (click)="agregar(p)">Agregar</button></td>
                </tr>
              }
            </tbody>
          </table>
        </section>

        <section class="tarjeta">
          <h2>Carrito</h2>
          @if (lineas().length === 0) {
            <p class="vacio">Aún no has agregado productos.</p>
          } @else {
            <table>
              <tbody>
                @for (l of lineas(); track l.nombre) {
                  <tr>
                    <td>{{ l.nombre }}</td>
                    <td>x{{ l.cantidad }}</td>
                    <td><button class="peligro" (click)="quitar(l.nombre)">−</button></td>
                  </tr>
                }
              </tbody>
            </table>
            <p><strong>Total: {{ total() | currency:'USD':'symbol':'1.0-2' }}</strong></p>
          }
          <button (click)="hacerPedido()" [disabled]="enviando() || lineas().length === 0">
            {{ enviando() ? 'Enviando…' : 'Hacer pedido' }}
          </button>
          @if (mensaje()) { <p class="aviso">{{ mensaje() }}</p> }
          @if (error()) { <p class="aviso error">{{ error() }}</p> }
        </section>
      </div>

      <div class="grid2">
        <section class="tarjeta">
          <h2>Mis pedidos</h2>
          @if (!cliente().trim()) {
            <p class="vacio">Escribe tu nombre arriba para ver tus pedidos.</p>
          } @else if (pedidos().length === 0) {
            <p class="vacio">No tienes pedidos todavía.</p>
          } @else {
            <table>
              <thead><tr><th>Pedido</th><th>Total</th><th>Estado</th><th></th></tr></thead>
              <tbody>
                @for (p of pedidos(); track p.id) {
                  <tr>
                    <td><span class="mono">{{ p.id.slice(0, 8) }}</span><br><small>{{ p.creadoEn | date:'short' }}</small></td>
                    <td>{{ p.total | currency:'USD':'symbol':'1.0-2' }}</td>
                    <td><span class="etiqueta" [class]="'etiqueta ' + p.estado">{{ p.estado }}</span></td>
                    <td><div class="fila">
                      @if (p.estado === 'CREADO') { <button (click)="pagar(p)">Pagar</button> }
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
          <h2>En vivo (todos los clientes)</h2>
          @if (tiempoReal.recientes().length === 0) {
            <p class="vacio">Esperando actividad…</p>
          }
          <ul class="feed">
            @for (n of tiempoReal.recientes(); track n.eventId) {
              <li>{{ n.tipo }}<small>{{ n.pedido?.cliente }} · {{ n.ocurridoEn | date:'mediumTime' }}</small></li>
            }
          </ul>
        </section>
      </div>
    </main>
  `,
})
export class AppComponent implements OnInit {
  private api = inject(ApiService);
  protected tiempoReal = inject(TiempoRealService);

  protected catalogo: Producto[] = [
    { nombre: 'Laptop', precio: 1200 }, { nombre: 'Monitor 27"', precio: 300 },
    { nombre: 'Teclado mecánico', precio: 60 }, { nombre: 'Mouse', precio: 25 },
    { nombre: 'Audífonos', precio: 80 }, { nombre: 'Webcam HD', precio: 45 },
  ];

  protected cliente = signal(this.leerCliente());
  private carrito = signal<Record<string, number>>({});
  protected pedidos = signal<PedidoVista[]>([]);
  protected enviando = signal(false);
  protected mensaje = signal('');
  protected error = signal('');

  protected lineas = computed(() =>
    Object.entries(this.carrito()).map(([nombre, cantidad]) => ({ nombre, cantidad })));
  protected total = computed(() =>
    this.lineas().reduce((suma, l) => suma + l.cantidad * this.precioDe(l.nombre), 0));

  ngOnInit(): void {
    this.tiempoReal.conectar();
    // Cada vez que el servidor hace broadcast, se recargan "mis pedidos"
    this.tiempoReal.eventos$.subscribe(() => this.cargar());
    this.cargar();
  }

  protected cambiarCliente(nombre: string): void {
    this.cliente.set(nombre);
    try { localStorage.setItem('cliente', nombre); } catch { /* sin almacenamiento */ }
    this.cargar();
  }

  protected agregar(p: Producto): void {
    this.carrito.update(c => ({ ...c, [p.nombre]: (c[p.nombre] ?? 0) + 1 }));
  }

  protected quitar(nombre: string): void {
    this.carrito.update(c => {
      const copia = { ...c };
      if ((copia[nombre] ?? 0) > 1) copia[nombre]--; else delete copia[nombre];
      return copia;
    });
  }

  protected hacerPedido(): void {
    this.error.set(''); this.mensaje.set('');
    if (!this.cliente().trim()) { this.error.set('Escribe tu nombre antes de hacer el pedido.'); return; }
    const items = this.lineas().map(l => ({ producto: l.nombre, cantidad: l.cantidad, precioUnitario: this.precioDe(l.nombre) }));
    this.enviando.set(true);
    this.api.crearPedido(this.cliente().trim(), items).subscribe({
      next: r => {
        this.carrito.set({});
        this.enviando.set(false);
        this.mensaje.set(`Pedido ${r.pedidoId.slice(0, 8)} recibido. Aparecerá en "Mis pedidos" en un instante.`);
        setTimeout(() => this.cargar(), 1500);   // respaldo por si el broadcast tarda
      },
      error: e => { this.enviando.set(false); this.error.set(this.textoError(e)); },
    });
  }

  protected pagar(p: PedidoVista): void { this.api.pagar(p.id).subscribe({ error: e => this.error.set(this.textoError(e)) }); }
  protected cancelar(p: PedidoVista): void { this.api.cancelar(p.id).subscribe({ error: e => this.error.set(this.textoError(e)) }); }

  private cargar(): void {
    const nombre = this.cliente().trim();
    if (!nombre) { this.pedidos.set([]); return; }
    this.api.listar({ cliente: nombre }).subscribe({
      next: lista => this.pedidos.set(lista),
      error: () => this.error.set('No se pudo consultar el servidor. ¿Está encendido en el puerto 8080?'),
    });
  }

  private precioDe(nombre: string): number {
    return this.catalogo.find(p => p.nombre === nombre)?.precio ?? 0;
  }

  private leerCliente(): string {
    try { return localStorage.getItem('cliente') ?? ''; } catch { return ''; }
  }

  private textoError(e: any): string {
    return e?.error?.error ?? 'No se pudo completar la operación. ¿Está encendido el servidor?';
  }
}
