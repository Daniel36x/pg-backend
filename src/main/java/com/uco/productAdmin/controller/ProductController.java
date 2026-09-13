package com.uco.productAdmin.controller;

import com.uco.productAdmin.dto.ProductRequestDTO;
import com.uco.productAdmin.models.Product;
import com.uco.productAdmin.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List; // Importante para el método getAllProducts

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 1. Crear el producto (POST)
    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequestDTO productDTO) {
        Product createdProduct = productService.createProduct(productDTO);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    // 2. Aplicar descuento por marca (PATCH)
    @PatchMapping("/discount")
    public ResponseEntity<String> applyDiscount(
            @RequestParam("brand") String brand,
            @RequestParam("percentage") BigDecimal percentage,
            @RequestParam("durationMinutes") Long durationMinutes) {

        productService.applyDiscountByBrand(brand, percentage, durationMinutes);
        return ResponseEntity.ok("Descuento del " + percentage + "% aplicado a la marca " + brand
                + " durante " + durationMinutes + " minutos");
    }

    // 3. Aplicar descuento por categoría (PATCH)
    @PatchMapping("/discount/category")
    public ResponseEntity<String> applyDiscountByCategory(
            @RequestParam("category") String category,
            @RequestParam("percentage") BigDecimal percentage,
            @RequestParam("durationMinutes") Long durationMinutes) {

        productService.applyDiscountByCategory(category, percentage, durationMinutes);
        return ResponseEntity.ok("Descuento del " + percentage + "% aplicado a la categoría " + category
                + " durante " + durationMinutes + " minutos");
    }

    // 4. Obtener todos los productos (GET)
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    // 5. Obtener un producto por ID (GET)
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable("id") Long id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }
}