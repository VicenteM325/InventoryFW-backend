package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.entities.Product;
import com.vm325.inventory_back.entities.StockAlert;
import com.vm325.inventory_back.enums.StockAlertStatus;
import com.vm325.inventory_back.reports.dto.InventoryAlertRowDto;
import com.vm325.inventory_back.reports.dto.InventoryProductRowDto;
import com.vm325.inventory_back.reports.dto.InventorySummaryDto;
import com.vm325.inventory_back.repositories.ProductRepository;
import com.vm325.inventory_back.repositories.StockAlertRepository;
import com.vm325.inventory_back.services.StockStatusCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryAnalyticsServiceImpl implements InventoryAnalyticsService {

    static final String AVAILABLE = "DISPONIBLE";
    static final String LOW = "STOCK_BAJO";
    static final String OUT = "AGOTADO";

    private final ProductRepository productRepository;
    private final StockAlertRepository stockAlertRepository;
    private final StockStatusCalculator stockStatusCalculator;

    @Override
    @Transactional(readOnly = true)
    public InventorySummaryDto summarize() {
        // Los productos dados de baja no son inventario: no cuentan ni se listan.
        List<InventoryProductRowDto> products = productRepository.findAll().stream()
                .filter(Product::isActive)
                .map(this::toRow)
                .sorted(Comparator.comparingInt((InventoryProductRowDto p) -> severity(p.status()))
                        .thenComparing(InventoryProductRowDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<InventoryAlertRowDto> alerts = stockAlertRepository.findAll().stream()
                .sorted(Comparator.comparing(StockAlert::getCreatedAt).reversed())
                .map(a -> new InventoryAlertRowDto(a.getStockAlertId(), a.getProduct().getName(),
                        a.getStockAtAlert(), a.getStatus().toString(), a.getCreatedAt(), a.getAttendedAt()))
                .toList();

        Map<String, Integer> byCategory = new LinkedHashMap<>();
        products.forEach(p -> byCategory.merge(p.category(), 1, Integer::sum));

        return new InventorySummaryDto(
                products.size(),
                count(products, AVAILABLE),
                count(products, LOW),
                count(products, OUT),
                (int) stockAlertRepository.findAll().stream().filter(a -> a.getStatus() == StockAlertStatus.PENDIENTE).count(),
                byCategory,
                products,
                alerts);
    }

    private InventoryProductRowDto toRow(Product p) {
        return new InventoryProductRowDto(
                p.getProductId(), p.getName(), p.getBarcode(),
                p.getCategory() != null ? p.getCategory().toString() : "-",
                p.getSupplier() != null ? p.getSupplier().getName() : "-",
                p.getStock(), p.getMinStock(), p.getPrice(),
                stockStatusCalculator.resolve(p.getStock(), p.getMinStock()));
    }

    /** Los productos que requieren atención van primero. */
    private int severity(String status) {
        return switch (status) {
            case OUT -> 0;
            case LOW -> 1;
            default -> 2;
        };
    }

    private int count(List<InventoryProductRowDto> products, String status) {
        return (int) products.stream().filter(p -> status.equals(p.status())).count();
    }
}
