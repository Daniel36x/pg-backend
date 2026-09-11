package com.uco.productAdmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    @NotNull(message = "El SKU no puede ser nulo")
    private Long sku;

    @NotNull(message = "El precio es obligatorio")
    private BigDecimal price;

    @NotNull(message = "El código de barras no puede ser nulo")
    private Long barCode;

    @NotBlank(message = "El nombre del producto no puede estar vacío")
    private String productName;

    @NotBlank(message = "La marca es obligatoria")
    private String brand;

    @NotNull(message = "El peso no puede ser nulo")
    private Double weight;

    @NotBlank(message = "La categoría es obligatoria")
    private String category;
}