package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.StockAlertResponseDto;
import com.vm325.inventory_back.entities.Product;
import com.vm325.inventory_back.entities.StockAlert;
import com.vm325.inventory_back.entities.Supplier;
import com.vm325.inventory_back.enums.StockAlertStatus;
import com.vm325.inventory_back.repositories.StockAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Pedido de reposición al proveedor del producto de una alerta de stock: el sistema arma el
 * correo con el producto de la alerta y el usuario solo elige la cantidad. Primero se puede ver
 * la vista previa y luego enviarlo; si el correo falla el error se propaga y no se guarda nada.
 */
@Service
@RequiredArgsConstructor
public class SupplierOrderService {

    private final StockAlertRepository stockAlertRepository;
    private final MailService mailService;
    private final StockAlertService stockAlertService;
    private final Clock clock;

    public MailService.RenderedMail preview(Long alertId, int quantity) {
        return mailService.previewSupplierOrder(buildOrder(findPendingAlert(alertId), quantity));
    }

    public StockAlertResponseDto send(Long alertId, int quantity) {
        StockAlert alert = findPendingAlert(alertId);
        mailService.sendSupplierOrder(buildOrder(alert, quantity));

        alert.setOrderedQuantity(quantity);
        alert.setOrderedAt(LocalDateTime.now(clock));
        stockAlertRepository.save(alert);
        return stockAlertService.toResponse(alert);
    }

    private StockAlert findPendingAlert(Long alertId) {
        StockAlert alert = stockAlertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Alerta no encontrada con id " + alertId));
        if (alert.getStatus() != StockAlertStatus.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La alerta ya fue atendida");
        }
        return alert;
    }

    private MailService.SupplierOrder buildOrder(StockAlert alert, int quantity) {
        if (quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad a pedir debe ser al menos 1");
        }
        Product product = alert.getProduct();
        Supplier supplier = product.getSupplier();
        if (supplier == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El producto no tiene un proveedor asociado");
        }
        String email = supplier.getEmail() == null ? "" : supplier.getEmail().trim();
        // ".invalid" es el valor provisional que dejó la migración para proveedores sin correo.
        if (email.isEmpty() || email.toLowerCase(Locale.ROOT).endsWith(".invalid")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Completa el correo del proveedor " + supplier.getName() + " antes de hacer el pedido");
        }
        return new MailService.SupplierOrder(email, supplier.getName(), product.getName(), product.getBarcode(),
                quantity, product.getStock(), product.getMinStock());
    }
}
