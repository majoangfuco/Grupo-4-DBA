package com.ecommerceb2b.backend.Controllers;

import com.ecommerceb2b.backend.Entities.AlmacenEntidad;
import com.ecommerceb2b.backend.Entities.StockAlmacenProductoDto;
import com.ecommerceb2b.backend.Repository.AlmacenRepositorio;
import com.ecommerceb2b.backend.Repository.StockAlmacenRepositorio;
import com.ecommerceb2b.backend.Util.CoordenadasNormalizador;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/almacenes")
@CrossOrigin(origins = "*")
public class AlmacenControlador {

    private final AlmacenRepositorio almacenRepositorio;
    private final StockAlmacenRepositorio stockAlmacenRepositorio;

    public AlmacenControlador(AlmacenRepositorio almacenRepositorio,
                              StockAlmacenRepositorio stockAlmacenRepositorio) {
        this.almacenRepositorio = almacenRepositorio;
        this.stockAlmacenRepositorio = stockAlmacenRepositorio;
    }

    // Listado normal (para tablas/administración)
    @GetMapping
    public ResponseEntity<List<AlmacenEntidad>> listar() {
        return ResponseEntity.ok(almacenRepositorio.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        return almacenRepositorio.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Almacén no encontrado")));
    }

    // Listado en GeoJSON, para pintar los almacenes en un mapa
    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listarGeoJson() {
        return ResponseEntity.ok(almacenRepositorio.findAllAsGeoJson());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody AlmacenEntidad a) {
        try {
            normalizarCoordenadas(a);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        Long id = almacenRepositorio.crear(a);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "almacen_id", id,
                "mensaje", "Almacén creado exitosamente"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id, @RequestBody AlmacenEntidad a) {
        try {
            normalizarCoordenadas(a);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        a.setAlmacenId(id);
        int filas = almacenRepositorio.actualizar(a);
        if (filas == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Almacén no encontrado"));
        }
        return ResponseEntity.ok(Map.of("mensaje", "Almacén actualizado exitosamente"));
    }

    // Acepta tanto { latitud, longitud } plano como GeoJSON Point
    // { type: "Point", coordinates: [lon, lat] } y deja la entidad normalizada.
    private void normalizarCoordenadas(AlmacenEntidad a) {
        CoordenadasNormalizador.Coordenadas coords = CoordenadasNormalizador.normalizar(
                a.getLatitud(), a.getLongitud(), a.getType(), a.getCoordinates()
        );
        a.setLatitud(coords.latitud());
        a.setLongitud(coords.longitud());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        int filas = almacenRepositorio.borrarPorId(id);
        if (filas == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Almacén no encontrado"));
        }
        return ResponseEntity.ok(Map.of("mensaje", "Almacén eliminado exitosamente"));
    }

    // ── Stock por almacén ──────────────────────────────────────────────────────

    // Lista los productos con el stock que tienen en este almacén.
    @GetMapping("/{id}/stock")
    public ResponseEntity<?> listarStock(@PathVariable Long id) {
        if (!stockAlmacenRepositorio.existeAlmacen(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Almacén no encontrado"));
        }
        List<StockAlmacenProductoDto> stock = stockAlmacenRepositorio.listarPorAlmacen(id);
        return ResponseEntity.ok(stock);
    }

    // Fija el stock de un producto en este almacén y recalcula el stock global
    // del producto (invariante: stock_global = SUMA de stock por almacén).
    @PutMapping("/{id}/stock")
    @Transactional
    public ResponseEntity<Map<String, Object>> actualizarStock(
            @PathVariable Long id,
            @RequestBody StockAlmacenProductoDto body) {

        if (!stockAlmacenRepositorio.existeAlmacen(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Almacén no encontrado"));
        }
        if (body.getProductoId() == null || !stockAlmacenRepositorio.existeProducto(body.getProductoId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        Integer nuevoStock = body.getStockDisponible();
        if (nuevoStock == null || nuevoStock < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El stock disponible debe ser un número mayor o igual a 0"));
        }

        // Verifica que el nuevo stock global no quede por debajo de lo ya reservado.
        int stockOtros = stockAlmacenRepositorio.sumarStockOtrosAlmacenes(id, body.getProductoId());
        int nuevoGlobal = stockOtros + nuevoStock;
        int reservado = stockAlmacenRepositorio.obtenerStockReservado(body.getProductoId());
        if (nuevoGlobal < reservado) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "El stock resultante (" + nuevoGlobal
                            + ") es menor al stock reservado (" + reservado + ") del producto"
            ));
        }

        stockAlmacenRepositorio.upsertStock(id, body.getProductoId(), nuevoStock);
        stockAlmacenRepositorio.recalcularStockGlobal(body.getProductoId());

        return ResponseEntity.ok(Map.of(
                "mensaje", "Stock actualizado exitosamente",
                "almacen_id", id,
                "producto_id", body.getProductoId(),
                "stock_disponible", nuevoStock,
                "stock_global", nuevoGlobal
        ));
    }
}
