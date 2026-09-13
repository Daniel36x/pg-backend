package com.uco.productAdmin.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sku;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private Long barCode;
    private String productName;
    private Double weight;

    // Nuevo campo para guardar la fecha de modificación
    @Column(name = "last_modified_date")
    private String lastModifiedDate;

    // Indica si el producto tiene una promoción/descuento activo
    @Column(name = "promo")
    private Boolean promo = false;

    // Precio original antes de aplicar la promoción, para poder revertirlo al vencer
    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    // Momento en el que la promoción vence y el precio debe volver al original
    @Column(name = "promo_ends_at")
    private LocalDateTime promoEndsAt;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "category_id")
    private Category category;

    // --- MÉTODOS DEL CICLO DE VIDA JPA ---

    // Este método se ejecuta automáticamente justo antes de CREAR (POST) el producto
    @PrePersist
    protected void onCreate() {
        this.lastModifiedDate = formatCurrentDate();
    }

    // Este método se ejecuta automáticamente justo antes de ACTUALIZAR (PATCH) el producto
    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedDate = formatCurrentDate();
    }

    // Método auxiliar para dar el formato exacto: aa/mm/dd:hh:mm:ss
    private String formatCurrentDate() {
        // En Java: 'yy' es año a 2 dígitos, 'MM' mes, 'dd' día, 'HH' hora 24h, 'mm' minutos, 'ss' segundos
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yy/MM/dd:HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}