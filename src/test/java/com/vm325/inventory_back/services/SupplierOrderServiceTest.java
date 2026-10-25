package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.StockAlertResponseDto;
import com.vm325.inventory_back.entities.Product;
import com.vm325.inventory_back.entities.StockAlert;
import com.vm325.inventory_back.entities.Supplier;
import com.vm325.inventory_back.enums.StockAlertStatus;
import com.vm325.inventory_back.repositories.StockAlertRepository;
import com.vm325.inventory_back.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupplierOrderServiceTest {

    private StockAlertRepository alerts;
    private MailService mail;
    private StockAlertService alertService;
    private SupplierOrderService service;

    @BeforeEach
    void setUp() {
        alerts = mock(StockAlertRepository.class);
        mail = mock(MailService.class);
        alertService = mock(StockAlertService.class);
        service = new SupplierOrderService(alerts, mail, alertService, new MutableClock());
    }

    private StockAlert alert(Supplier supplier, StockAlertStatus status) {
        Product product = Product.builder().productId(1L).name("Funda").barcode("750100").stock(3).minStock(3)
                .supplier(supplier).build();
        return StockAlert.builder().stockAlertId(9L).product(product).stockAtAlert(3).status(status).build();
    }

    private Supplier supplier(String email) {
        return Supplier.builder().supplierId(5L).name("Tigo").email(email).phone("55551111").build();
    }

    private void found(StockAlert alert) {
        when(alerts.findById(9L)).thenReturn(Optional.of(alert));
    }

    private ResponseStatusException failure(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run);
    }

    @Test
    void sendsOrderWithProductOfTheAlertAndStoresIt() {
        StockAlert alert = alert(supplier("ventas@tigo.com"), StockAlertStatus.PENDIENTE);
        found(alert);
        when(alertService.toResponse(alert)).thenReturn(StockAlertResponseDto.builder().stockAlertId(9L).build());

        StockAlertResponseDto result = service.send(9L, 20);

        verify(mail).sendSupplierOrder(new MailService.SupplierOrder(
                "ventas@tigo.com", "Tigo", "Funda", "750100", 20, 3, 3));
        assertEquals(20, alert.getOrderedQuantity());
        assertNotNull(alert.getOrderedAt());
        verify(alerts).save(alert);
        assertEquals(9L, result.getStockAlertId());
    }

    @Test
    void previewDoesNotSendNorStore() {
        StockAlert alert = alert(supplier("ventas@tigo.com"), StockAlertStatus.PENDIENTE);
        found(alert);

        service.preview(9L, 5);

        verify(mail).previewSupplierOrder(any());
        verify(mail, never()).sendSupplierOrder(any());
        verify(alerts, never()).save(any());
    }

    @Test
    void mailFailureDoesNotStoreTheOrder() {
        StockAlert alert = alert(supplier("ventas@tigo.com"), StockAlertStatus.PENDIENTE);
        found(alert);
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(mail).sendSupplierOrder(any());

        assertEquals(503, failure(() -> service.send(9L, 10)).getStatusCode().value());
        assertNull(alert.getOrderedQuantity());
        verify(alerts, never()).save(any());
    }

    @Test
    void rejectsProductWithoutSupplier() {
        found(alert(null, StockAlertStatus.PENDIENTE));
        assertEquals(409, failure(() -> service.send(9L, 10)).getStatusCode().value());
        verifyNoInteractions(mail);
    }

    @Test
    void rejectsProvisionalSupplierEmail() {
        found(alert(supplier("sin-correo-5@proveedor.invalid"), StockAlertStatus.PENDIENTE));
        ResponseStatusException ex = failure(() -> service.send(9L, 10));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Tigo"));
        verifyNoInteractions(mail);
    }

    @Test
    void rejectsInvalidQuantityAttendedAlertAndMissingAlert() {
        found(alert(supplier("ventas@tigo.com"), StockAlertStatus.PENDIENTE));
        assertEquals(400, failure(() -> service.send(9L, 0)).getStatusCode().value());

        found(alert(supplier("ventas@tigo.com"), StockAlertStatus.ATENDIDA));
        assertEquals(409, failure(() -> service.send(9L, 10)).getStatusCode().value());

        when(alerts.findById(9L)).thenReturn(Optional.empty());
        assertEquals(404, failure(() -> service.send(9L, 10)).getStatusCode().value());
        verifyNoInteractions(mail);
    }
}
