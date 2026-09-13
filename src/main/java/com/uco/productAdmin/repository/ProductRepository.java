package com.uco.productAdmin.repository;

import com.uco.productAdmin.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Fíjate en el guion bajo: Brand_Name
    List<Product> findByBrand_Name(String name);
    List<Product> findByCategory_Name(String categoryName);
    List<Product> findByPromoTrueAndPromoEndsAtBefore(LocalDateTime now);
}