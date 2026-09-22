package com.uco.productAdmin.controller;

import com.uco.productAdmin.dto.ProductRequestDTO;
import com.uco.productAdmin.models.Product;
import com.uco.productAdmin.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List; // Importante para el método getAllProducts

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 1. Crear el producto (POST) - Solo ADMIN
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequestDTO productDTO) {
        Product createdProduct = productService.createProduct(productDTO);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    // 2. Aplicar descuento por marca (PATCH) - ADMIN o EMPLEADO
    @PatchMapping("/discount")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<String> applyDiscount(
            @RequestParam("brand") String brand,
            @RequestParam("percentage") BigDecimal percentage,
            @RequestParam("durationMinutes") Long durationMinutes,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate) {

        productService.applyDiscountByBrand(brand, percentage, durationMinutes, startDate);
        return ResponseEntity.ok(buildDiscountMessage("la marca " + brand, percentage, durationMinutes, startDate));
    }

    // 3. Aplicar descuento por categoría (PATCH) - ADMIN o EMPLEADO
    @PatchMapping("/discount/category")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<String> applyDiscountByCategory(
            @RequestParam("category") String category,
            @RequestParam("percentage") BigDecimal percentage,
            @RequestParam("durationMinutes") Long durationMinutes,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate) {

        productService.applyDiscountByCategory(category, percentage, durationMinutes, startDate);
        return ResponseEntity.ok(buildDiscountMessage("la categoría " + category, percentage, durationMinutes, startDate));
    }

    // 4b. Aplicar descuento por SKU (PATCH) - ADMIN o EMPLEADO
    @PatchMapping("/discount/sku")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<String> applyDiscountBySku(
            @RequestParam("sku") Long sku,
            @RequestParam("percentage") BigDecimal percentage,
            @RequestParam("durationMinutes") Long durationMinutes,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate) {

        productService.applyDiscountBySku(sku, percentage, durationMinutes, startDate);
        return ResponseEntity.ok(buildDiscountMessage("el SKU " + sku, percentage, durationMinutes, startDate));
    }

    // --- Construye el mensaje de confirmación, indicando si el descuento se aplicó ya o quedó programado ---
    private String buildDiscountMessage(String target, BigDecimal percentage, Long durationMinutes, LocalDateTime startDate) {
        if (startDate != null && startDate.isAfter(LocalDateTime.now())) {
            return "Descuento del " + percentage + "% programado para " + target
                    + " a partir del " + startDate + ", activo durante " + durationMinutes + " minutos";
        }
        return "Descuento del " + percentage + "% aplicado a " + target
                + " durante " + durationMinutes + " minutos";
    }

    // 4. Actualizar un producto de forma permanente por ID (PUT) - ADMIN o EMPLEADO
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<Product> updateProduct(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProductRequestDTO productDTO) {
        Product updatedProduct = productService.updateProduct(id, productDTO);
        return ResponseEntity.ok(updatedProduct);
    }

    // 5. Obtener todos los productos (GET) - ADMIN o EMPLEADO autenticados
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    // 6. Obtener un producto por ID (GET) - ADMIN o EMPLEADO autenticados
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<Product> getProductById(@PathVariable("id") Long id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    // 7. Eliminar un producto de forma permanente por SKU (DELETE) - Solo ADMIN
    @DeleteMapping("/sku/{sku}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProductBySku(@PathVariable("sku") Long sku) {
        productService.deleteProductBySku(sku);
        return ResponseEntity.noContent().build();
    }
}