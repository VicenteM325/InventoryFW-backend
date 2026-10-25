package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.entities.Sale;
import com.vm325.inventory_back.entities.SaleDetail;
import com.vm325.inventory_back.reports.dto.DailySalesDto;
import com.vm325.inventory_back.reports.dto.EmployeeSalesDto;
import com.vm325.inventory_back.reports.dto.ProductSalesDto;
import com.vm325.inventory_back.reports.dto.SaleRowDto;
import com.vm325.inventory_back.reports.dto.SalesSummaryDto;
import com.vm325.inventory_back.reports.dto.SalesTotalsDto;
import com.vm325.inventory_back.repositories.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesAnalyticsServiceImpl implements SalesAnalyticsService {

    /** Tope del rango para que un reporte no cargue años de ventas en memoria. */
    static final int MAX_RANGE_DAYS = 366;
    static final int TOP_PRODUCTS = 10;

    private final SaleRepository saleRepository;

    @Override
    @Transactional(readOnly = true)
    public SalesSummaryDto summarize(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha 'from' no puede ser posterior a 'to'");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rango máximo es de " + MAX_RANGE_DAYS + " días");
        }

        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);

        List<Sale> sales = load(from, to);
        List<Sale> previousSales = load(previousFrom, previousTo);

        SalesTotalsDto current = totals(sales);
        SalesTotalsDto previous = totals(previousSales);

        return new SalesSummaryDto(
                from, to, previousFrom, previousTo,
                current, previous,
                changePct(current.total(), previous.total()),
                changePct(BigDecimal.valueOf(current.transactions()), BigDecimal.valueOf(previous.transactions())),
                changePct(current.averageTicket(), previous.averageTicket()),
                changePct(BigDecimal.valueOf(current.units()), BigDecimal.valueOf(previous.units())),
                byDay(sales, from, to),
                byEmployee(sales),
                topProducts(sales),
                rows(sales));
    }

    private List<Sale> load(LocalDate from, LocalDate to) {
        return saleRepository.findForReport(from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1));
    }

    private SalesTotalsDto totals(List<Sale> sales) {
        BigDecimal total = sales.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int units = sales.stream().mapToInt(this::unitsOf).sum();
        BigDecimal average = sales.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP);
        return new SalesTotalsDto(total.setScale(2, RoundingMode.HALF_UP), sales.size(), average, units);
    }

    /** Variación porcentual; null si el periodo anterior es cero (no hay base de comparación). */
    static BigDecimal changePct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private List<DailySalesDto> byDay(List<Sale> sales, LocalDate from, LocalDate to) {
        Map<LocalDate, List<Sale>> grouped = sales.stream()
                .collect(Collectors.groupingBy(s -> s.getDate().toLocalDate()));
        List<DailySalesDto> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            List<Sale> daySales = grouped.getOrDefault(day, List.of());
            BigDecimal total = daySales.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new DailySalesDto(day, total.setScale(2, RoundingMode.HALF_UP), daySales.size()));
        }
        return result;
    }

    private List<EmployeeSalesDto> byEmployee(List<Sale> sales) {
        Map<String, List<Sale>> grouped = sales.stream()
                .collect(Collectors.groupingBy(s -> s.getEmployee().getUserName(), LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> new EmployeeSalesDto(
                        e.getValue().get(0).getEmployee().getName(),
                        e.getKey(),
                        e.getValue().stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(2, RoundingMode.HALF_UP),
                        e.getValue().size()))
                .sorted(Comparator.comparing(EmployeeSalesDto::total).reversed())
                .toList();
    }

    private List<ProductSalesDto> topProducts(List<Sale> sales) {
        Map<Long, ProductAccumulator> grouped = new LinkedHashMap<>();
        for (Sale sale : sales) {
            for (SaleDetail detail : sale.getDetails()) {
                grouped.computeIfAbsent(detail.getProduct().getProductId(),
                                id -> new ProductAccumulator(id, detail.getProduct().getName()))
                        .add(detail.getAmount(), detail.getUnitPrice());
            }
        }
        return grouped.values().stream()
                .map(a -> new ProductSalesDto(a.id, a.name, a.units, a.revenue.setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(ProductSalesDto::units).reversed()
                        .thenComparing(Comparator.comparing(ProductSalesDto::revenue).reversed()))
                .limit(TOP_PRODUCTS)
                .toList();
    }

    private List<SaleRowDto> rows(List<Sale> sales) {
        // Las más recientes primero: es lo que se espera ver en una tabla de detalle.
        return sales.stream()
                .sorted(Comparator.comparing(Sale::getDate).reversed())
                .map(s -> new SaleRowDto(
                        s.getSalesId(),
                        s.getDate(),
                        s.getEmployee().getUserName(),
                        s.getCustomerName() == null || s.getCustomerName().isBlank() ? "-" : s.getCustomerName(),
                        s.getDetails().stream()
                                .map(d -> d.getProduct().getName() + " x" + d.getAmount())
                                .collect(Collectors.joining(", ")),
                        unitsOf(s),
                        s.getTotal()))
                .toList();
    }

    private int unitsOf(Sale sale) {
        return sale.getDetails().stream().mapToInt(SaleDetail::getAmount).sum();
    }

    private static final class ProductAccumulator {
        private final Long id;
        private final String name;
        private int units;
        private BigDecimal revenue = BigDecimal.ZERO;

        ProductAccumulator(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        void add(int amount, BigDecimal unitPrice) {
            units += amount;
            revenue = revenue.add(unitPrice.multiply(BigDecimal.valueOf(amount)));
        }
    }
}
