package com.uco.productAdmin.services;

import com.uco.productAdmin.dto.ProductRequestDTO;
import com.uco.productAdmin.models.Brand;
import com.uco.productAdmin.models.Category;
import com.uco.productAdmin.models.Product;
import com.uco.productAdmin.repository.BrandRepository;
import com.uco.productAdmin.repository.CategoryRepository;
import com.uco.productAdmin.repository.ProductRepository;
import com.uco.productAdmin.mqtt.MqttPub; // <-- Importación de tu clase MQTT
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    // Inyectamos tu clase publicadora de MQTT
    private final MqttPub mqttPub;

    // --- Método Auxiliar para construir y enviar el JSON por MQTT ---
    private void notificarMQTT(Product product) {
        // Envolvemos TODO en comillas dobles (\"%s\") para que el JSON final tenga puros Strings
        String jsonPayload = String.format(
                "{\n" +
                        "  \"sku\": \"%s\",\n" +
                        "  \"price\": \"%s\",\n" +
                        "  \"barCode\": \"%s\",\n" +
                        "  \"productName\": \"%s\",\n" +
                        "  \"date\": \"%s\",\n" +
                        "  \"promo\": \"%s\"\n" +
                        "}",
                String.valueOf(product.getSku()),
                formatPriceCOP(product.getPrice()), // Llamamos al nuevo formateador
                String.valueOf(product.getBarCode()),
                product.getProductName(),
                product.getLastModifiedDate() != null ? product.getLastModifiedDate() : "N/A",
                String.valueOf(Boolean.TRUE.equals(product.getPromo()))
        );

        // El tópico ahora es solo la categoría
        String topicDinamico = product.getCategory().getName().replaceAll("\\s+", "_").toLowerCase();

        // Llamamos al publicador enviando la categoría directa (ej: "domestico")
        mqttPub.publicar(topicDinamico, jsonPayload);
    }

    // --- NUEVO: Método para dar formato de moneda colombiana ---
    private String formatPriceCOP(BigDecimal price) {
        if (price == null) return "0";

        // 1. Formateamos todo con puntos para los miles (ej: 1.850.000)
        DecimalFormat df = new DecimalFormat("#,##0");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        df.setDecimalFormatSymbols(symbols);
        String formatted = df.format(price.longValue());

        // 2. Si el valor es de un millón o más, cambiamos el punto del millón por la comilla (')
        if (price.longValue() >= 1_000_000) {
            // Buscamos la posición del separador de millones (8 caracteres contando desde el final: .000.000)
            int millionDotIndex = formatted.length() - 8;
            if (millionDotIndex >= 0 && formatted.charAt(millionDotIndex) == '.') {
                formatted = formatted.substring(0, millionDotIndex) + "'" + formatted.substring(millionDotIndex + 1);
            }
        }

        return formatted;
    }

    @Transactional
    public Product createProduct(ProductRequestDTO dto) {
        // 1. Buscar o crear la Marca (Brand)
        Brand brand = brandRepository.findByName(dto.getBrand())
                .orElseGet(() -> {
                    Brand newBrand = new Brand();
                    newBrand.setName(dto.getBrand());
                    return brandRepository.save(newBrand);
                });

        // 2. Buscar o crear la Categoría (Category)
        Category category = categoryRepository.findByName(dto.getCategory())
                .orElseGet(() -> {
                    Category newCategory = new Category();
                    newCategory.setName(dto.getCategory());
                    return categoryRepository.save(newCategory);
                });

        // 3. Mapear DTO a Entidad Product
        Product product = new Product();
        product.setSku(dto.getSku());
        product.setPrice(dto.getPrice());
        product.setBarCode(dto.getBarCode());
        product.setProductName(dto.getProductName());
        product.setWeight(dto.getWeight());
        product.setBrand(brand);
        product.setCategory(category);
        product.setPromo(false);

        // 4. Guardar en BD
        Product savedProduct = productRepository.save(product);

        // 5. Notificar a MQTT que se creó un nuevo producto
        notificarMQTT(savedProduct);

        return savedProduct;
    }

    // --- Actualiza un producto existente de forma permanente (reemplaza sus datos) ---
    @Transactional
    public Product updateProduct(Long id, ProductRequestDTO dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con el ID: " + id));

        // 1. Buscar o crear la Marca (Brand)
        Brand brand = brandRepository.findByName(dto.getBrand())
                .orElseGet(() -> {
                    Brand newBrand = new Brand();
                    newBrand.setName(dto.getBrand());
                    return brandRepository.save(newBrand);
                });

        // 2. Buscar o crear la Categoría (Category)
        Category category = categoryRepository.findByName(dto.getCategory())
                .orElseGet(() -> {
                    Category newCategory = new Category();
                    newCategory.setName(dto.getCategory());
                    return categoryRepository.save(newCategory);
                });

        // 3. Sobrescribir los datos del producto con los nuevos valores
        product.setSku(dto.getSku());
        product.setPrice(dto.getPrice());
        product.setBarCode(dto.getBarCode());
        product.setProductName(dto.getProductName());
        product.setWeight(dto.getWeight());
        product.setBrand(brand);
        product.setCategory(category);

        // 4. Al ser una modificación permanente, se cancela cualquier promoción activa
        // para que el precio nuevo no sea revertido por revertirPromocionesVencidas()
        product.setPromo(false);
        product.setOriginalPrice(null);
        product.setPromoEndsAt(null);

        // 5. Guardar en BD
        Product updatedProduct = productRepository.save(product);

        // 6. Notificar a MQTT que el producto fue actualizado
        notificarMQTT(updatedProduct);

        return updatedProduct;
    }

    @Transactional
    public void applyDiscountByBrand(String brandName, BigDecimal discountPercentage, Long durationMinutes) {
        List<Product> products = productRepository.findByBrand_Name(brandName);

        if (products.isEmpty()) {
            throw new RuntimeException("No se encontraron productos para la marca: " + brandName);
        }

        applyDiscount(products, discountPercentage, durationMinutes);
    }

    @Transactional
    public void applyDiscountByCategory(String categoryName, BigDecimal discountPercentage, Long durationMinutes) {
        List<Product> products = productRepository.findByCategory_Name(categoryName);

        if (products.isEmpty()) {
            throw new RuntimeException("No se encontraron productos para la categoría: " + categoryName);
        }

        applyDiscount(products, discountPercentage, durationMinutes);
    }

    // --- Aplica el descuento, marca la promo como activa y programa su vencimiento ---
    private void applyDiscount(List<Product> products, BigDecimal discountPercentage, Long durationMinutes) {
        BigDecimal divisor = new BigDecimal("100");
        BigDecimal discount = discountPercentage.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal multiplier = BigDecimal.ONE.subtract(discount);
        LocalDateTime promoEndsAt = LocalDateTime.now().plusMinutes(durationMinutes);

        products.forEach(product -> {
            // Si ya hay una promo activa usamos el precio original guardado como base,
            // para no encadenar descuentos sobre un precio ya rebajado.
            BigDecimal basePrice = Boolean.TRUE.equals(product.getPromo()) && product.getOriginalPrice() != null
                    ? product.getOriginalPrice()
                    : product.getPrice();

            BigDecimal newPrice = basePrice.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);

            product.setOriginalPrice(basePrice);
            product.setPrice(newPrice);
            product.setPromo(true);
            product.setPromoEndsAt(promoEndsAt);
        });

        // Guardamos los cambios en BD
        List<Product> savedProducts = productRepository.saveAll(products);

        // Notificamos a MQTT
        savedProducts.forEach(this::notificarMQTT);
    }

    // --- Revisa periódicamente las promociones vencidas y restaura el precio original ---
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void revertirPromocionesVencidas() {
        List<Product> vencidos = productRepository.findByPromoTrueAndPromoEndsAtBefore(LocalDateTime.now());

        if (vencidos.isEmpty()) {
            return;
        }

        vencidos.forEach(product -> {
            product.setPrice(product.getOriginalPrice());
            product.setPromo(false);
            product.setOriginalPrice(null);
            product.setPromoEndsAt(null);
        });

        List<Product> savedProducts = productRepository.saveAll(vencidos);

        savedProducts.forEach(this::notificarMQTT);
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con el ID: " + id));
    }
}