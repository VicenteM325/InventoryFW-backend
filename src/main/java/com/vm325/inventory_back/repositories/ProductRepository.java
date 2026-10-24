package com.vm325.inventory_back.repositories;

import com.vm325.inventory_back.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBarcode(String barcode);
    boolean existsBySupplier_SupplierId(Long supplierId);

    /**
     * Lee el producto con bloqueo de escritura (SELECT ... FOR UPDATE) para que
     * dos ventas simultáneas no descuenten el mismo stock a la vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :id")
    Optional<Product> findByIdForUpdate(Long id);
}
