package com.uco.productAdmin.controller;

import com.uco.productAdmin.dto.ProductRequestDTO;
import com.uco.productAdmin.models.Product;
import com.uco.productAdmin.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
            @RequestParam("durationMinutes") Long durationMinutes) {

        productService.applyDiscountByBrand(brand, percentage, durationMinutes);
        return ResponseEntity.ok("Descuento del " + percentage + "% aplicado a la marca " + brand
                + " durante " + durationMinutes + " minutos");
    }

    // 3. Aplicar descuento por categoría (PATCH) - ADMIN o EMPLEADO
    @PatchMapping("/discount/category")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<String> applyDiscountByCategory(
            @RequestParam("category") String category,
            @RequestParam("percentage") BigDecimal percentage,
            @RequestParam("durationMinutes") Long durationMinutes) {

        productService.applyDiscountByCategory(category, percentage, durationMinutes);
        return ResponseEntity.ok("Descuento del " + percentage + "% aplicado a la categoría " + category
                + " durante " + durationMinutes + " minutos");
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
}