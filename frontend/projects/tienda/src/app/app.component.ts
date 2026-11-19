import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '@shared/api.service';
import { TiempoRealService } from '@shared/tiempo-real.service';
import { TrazaService } from '@shared/traza.service';
import { LogStackComponent } from '@shared/log-stack.component';
import { PedidoVista } from '@shared/models';
import { ESTADO_TEXTO, RUTA_PEDIDO, frase, pasosCumplidos } from '@shared/presentacion';

interface Producto { nombre: string; detalle: string; precio: number; }
type Vista = 'comprar' | 'pedidos' | 'log';

/**
 * CLIENTE 1 - TIENDA. Aplicación independiente del servidor:
 *  - envía COMANDOS (crear / pagar / cancelar pedido)
 *  - lee CONSULTAS (mis pedidos)
 *  - escucha el BROADCAST por WebSocket para actualizarse sin recargar
 *  - muestra el LOG DEL STACK: cuándo se usa cada herramienta.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, DatePipe, LogStackComponent],
  template: `
    <header class="top">
      <div class="marca">Tienda en vivo</div>
      <nav class="tabs" role="tablist" aria-label="Secciones">
        <button type="button" role="tab" [attr.aria-selected]="vista() === 'comprar'" (click)="ir('comprar')">Comprar</button>
        <button type="button" role="tab" [attr.aria-selected]="vista() === 'pedidos'" (click)="ir('pedidos')">
          Mis pedidos
          @if (activos() > 0) { <span class="insignia">{{ activos() }}</span> }
        </button>
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
        @case ('comprar') {
          <div class="dos-col">
            <section>
              <div class="encabezado">
                <h2>Catálogo</h2>
                <p class="suave">Agrega lo que quieras y confirma tu pedido en el recibo.</p>
              </div>
              @for (p of catalogo; track p.nombre) {
                <div class="producto">
                  <div>
                    <h3>{{ p.nombre }}</h3>
                    <p>{{ p.detalle }}</p>
                  </div>
                  <span class="precio">{{ p.precio | currency:'USD':'symbol':'1.0-2' }}</span>
                  @if (cantidadDe(p.nombre) === 0) {
                    <button type="button" class="secundario" (click)="agregar(p)">Agregar</button>
                  } @else {
                    <span class="cantidad">
                      <button type="button" class="secundario mini" (click)="quitar(p.nombre)" [attr.aria-label]="'Quitar uno de ' + p.nombre">−</button>
                      {{ cantidadDe(p.nombre) }}
                      <button type="button" class="secundario mini" (click)="agregar(p)" [attr.aria-label]="'Agregar otro ' + p.nombre">+</button>
                    </span>
                  }
                </div>
              }
            </section>

            <aside class="recibo" aria-label="Tu pedido">
              <h2>Tu pedido</h2>
              <div class="campo">
                <label for="cliente">A nombre de</label>
                <input id="cliente" placeholder="Ej: Ana Gómez" autocomplete="name"
                       [ngModel]="cliente()" (ngModelChange)="cambiarCliente($event)" />
              </div>
              @if (lineas().length === 0) {
                <p class="vacio">Tu recibo está vacío. Agrega productos del catálogo.</p>
              } @else {
                <ul>
                  @for (l of lineas(); track l.nombre) {
                    <li><span>{{ l.cantidad }} × {{ l.nombre }}</span>
                      <span class="total-fila">{{ l.cantidad * precioDe(l.nombre) | currency:'USD':'symbol':'1.0-2' }}</span></li>
                  }
                </ul>
                <div class="total"><span>Total</span><strong>{{ total() | currency:'USD':'symbol':'1.0-2' }}</strong></div>
              }
              <button type="button" class="principal" (click)="hacerPedido()" [disabled]="enviando() || lineas().length === 0">
                {{ enviando() ? 'Enviando…' : 'Hacer pedido' }}
              </button>
              @if (mensaje()) {
                <p class="aviso">{{ mensaje() }} <button type="button" class="secundario" (click)="ir('pedidos')">Ver mis pedidos</button></p>
              }
              @if (error()) { <p class="aviso error">{{ error() }}</p> }
            </aside>
          </div>
        }

        @case ('pedidos') {
          <div class="dos-col">
            <section>
              <div class="encabezado">
                <h2>Mis pedidos</h2>
                <p class="suave">Se actualizan solos cuando el servidor avisa por WebSocket.</p>
              </div>
              @if (!cliente().trim()) {
                <p class="vacio">Escribe tu nombre en "Comprar" para ver tus pedidos.</p>
              } @else if (pedidos().length === 0) {
                <p class="vacio">Todavía no tienes pedidos. Haz el primero desde "Comprar".</p>
              } @else {
                @for (p of pedidos(); track p.id) {
                  <article class="pedido">
                    <div>
                      <span class="mono">{{ p.id.slice(0, 8) }}</span>
                      <div class="suave" style="font-size:.82rem">{{ p.creadoEn | date:'d MMM, h:mm a' }}</div>
                    </div>
                    @if (p.estado === 'CANCELADO') {
                      <span class="cancelado-marca">Cancelado</span>
                    } @else {
                      <div class="ruta" [attr.aria-label]="'Estado: ' + textoEstado(p)">
                        @for (r of ruta; track r.clave; let i = $index; let ultimo = $last) {
                          <span class="paso" [class.hecho]="i < pasos(p)" [class.actual]="i === pasos(p) - 1" [style.--c]="'var(--' + r.clave.toLowerCase() + ')'">{{ r.texto }}</span>
                          @if (!ultimo) { <span class="union" [class.hecha]="i < pasos(p) - 1"></span> }
                        }
                      </div>
                    }
                    <span class="total-fila">{{ p.total | currency:'USD':'symbol':'1.0-2' }}</span>
                    <div class="acciones-fila">
                      @if (p.estado === 'CREADO') { <button type="button" (click)="pagar(p)">Pagar</button> }
                      @if (p.estado === 'CREADO' || p.estado === 'PAGADO') {
                        <button type="button" class="peligro" (click)="cancelar(p)">Cancelar</button>
                      }
                    </div>
                  </article>
                }
              }
              @if (error()) { <p class="aviso error">{{ error() }}</p> }
            </section>

            <aside class="vivo">
              <h2>Pasando ahora en la tienda</h2>
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

  protected catalogo: Producto[] = [
    { nombre: 'Laptop', detalle: 'Portátil de 14 pulgadas, 16 GB de RAM', precio: 1200 },
    { nombre: 'Monitor 27"', detalle: 'Panel IPS, resolución 4K', precio: 300 },
    { nombre: 'Teclado mecánico', detalle: 'Switches rojos, retroiluminado', precio: 60 },
    { nombre: 'Mouse', detalle: 'Inalámbrico, recargable', precio: 25 },
    { nombre: 'Audífonos', detalle: 'Con cancelación de ruido', precio: 80 },
    { nombre: 'Webcam HD', detalle: '1080p con micrófono integrado', precio: 45 },
  ];
  protected ruta = RUTA_PEDIDO;

  protected vista = signal<Vista>('comprar');
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
  protected activos = computed(() =>
    this.pedidos().filter(p => p.estado === 'CREADO' || p.estado === 'PAGADO').length);

  ngOnInit(): void {
    document.body.dataset['app'] = 'tienda';
    this.tiempoReal.conectar();
    // Cada vez que el servidor hace broadcast, se recargan "mis pedidos"
    this.tiempoReal.eventos$.subscribe(() => this.cargar());
    this.cargar();
  }

  protected ir(v: Vista): void {
    this.vista.set(v);
    if (v === 'log') this.traza.marcarLeido();
  }

  protected cambiarCliente(nombre: string): void {
    this.cliente.set(nombre);
    try { localStorage.setItem('cliente', nombre); } catch { /* sin almacenamiento */ }
    this.cargar();
  }

  protected cantidadDe(nombre: string): number { return this.carrito()[nombre] ?? 0; }

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
        this.mensaje.set(`Pedido ${r.pedidoId.slice(0, 8)} recibido.`);
        setTimeout(() => this.cargar(), 1500);   // respaldo por si el broadcast tarda
      },
      error: e => { this.enviando.set(false); this.error.set(this.textoError(e)); },
    });
  }

  protected pagar(p: PedidoVista): void { this.api.pagar(p.id).subscribe({ error: e => this.error.set(this.textoError(e)) }); }
  protected cancelar(p: PedidoVista): void { this.api.cancelar(p.id).subscribe({ error: e => this.error.set(this.textoError(e)) }); }

  protected pasos(p: PedidoVista): number { return pasosCumplidos(p.estado); }
  protected textoEstado(p: PedidoVista): string { return ESTADO_TEXTO[p.estado]; }
  protected fraseDe = frase;

  protected precioDe(nombre: string): number {
    return this.catalogo.find(p => p.nombre === nombre)?.precio ?? 0;
  }

  private cargar(): void {
    const nombre = this.cliente().trim();
    if (!nombre) { this.pedidos.set([]); return; }
    this.api.listar({ cliente: nombre }).subscribe({
      next: lista => { this.pedidos.set(lista); this.error.set(''); },
      error: () => this.error.set('No se pudo consultar el servidor. ¿Está encendido?'),
    });
  }

  private leerCliente(): string {
    try { return localStorage.getItem('cliente') ?? ''; } catch { return ''; }
  }

  private textoError(e: any): string {
    return e?.error?.error ?? 'No se pudo completar la operación. ¿Está encendido el servidor?';
  }
}
