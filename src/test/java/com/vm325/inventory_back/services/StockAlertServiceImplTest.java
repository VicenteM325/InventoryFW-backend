package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.StockAlertResponseDto;
import com.vm325.inventory_back.entities.Product;
import com.vm325.inventory_back.entities.StockAlert;
import com.vm325.inventory_back.entities.Supplier;
import com.vm325.inventory_back.enums.StockAlertStatus;
import com.vm325.inventory_back.repositories.StockAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StockAlertServiceImplTest {

    private StockAlertRepository repository;
    private StockAlertServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(StockAlertRepository.class);
        when(repository.save(any(StockAlert.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new StockAlertServiceImpl(repository);
    }

    private StockAlert alert(Supplier supplier, LocalDateTime orderedAt) {
        Product product = Product.builder().productId(1L).name("Funda").stock(3).minStock(3).supplier(supplier).build();
        StockAlert alert = StockAlert.builder().stockAlertId(9L).product(product).stockAtAlert(3)
                .status(StockAlertStatus.PENDIENTE).orderedAt(orderedAt).build();
        when(repository.findById(9L)).thenReturn(Optional.of(alert));
        return alert;
    }

    private Supplier supplier() {
        return Supplier.builder().supplierId(5L).name("Tigo").email("ventas@tigo.com").phone("55551111").build();
    }

    @Test
    void alertWithSupplierCannotBeAttendedBeforeTheOrderIsSent() {
        alert(supplier(), null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.attend(9L));

        assertEquals(409, ex.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void alertWithSupplierCanBeAttendedOnceTheOrderWasSent() {
        alert(supplier(), LocalDateTime.of(2026, 3, 5, 10, 0));

        StockAlertResponseDto result = service.attend(9L);

        assertEquals(StockAlertStatus.ATENDIDA, result.getStatus());
        assertNotNull(result.getOrderedAt());
    }

    @Test
    void alertWithoutSupplierCanBeAttendedDirectly() {
        alert(null, null);

        assertEquals(StockAlertStatus.ATENDIDA, service.attend(9L).getStatus());
    }
}
