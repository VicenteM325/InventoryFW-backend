package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.entities.Product;
import com.vm325.inventory_back.entities.Sale;
import com.vm325.inventory_back.entities.SaleDetail;
import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.reports.dto.SalesSummaryDto;
import com.vm325.inventory_back.repositories.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesAnalyticsServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 10);
    private static final LocalDate TO = LocalDate.of(2026, 3, 12);

    private SaleRepository repo;
    private SalesAnalyticsServiceImpl service;
    private User ana;
    private User luis;
    private Product phone;
    private Product case_;

    @BeforeEach
    void setUp() {
        repo = mock(SaleRepository.class);
        service = new SalesAnalyticsServiceImpl(repo);
        ana = user("ana", "Ana Pérez");
        luis = user("luis", "Luis Gómez");
        phone = product(1L, "Celular X");
        case_ = product(2L, "Funda");
    }

    private User user(String userName, String name) {
        User u = new User();
        u.setUserName(userName);
        u.setName(name);
        return u;
    }

    private Product product(Long id, String name) {
        Product p = new Product();
        p.setProductId(id);
        p.setName(name);
        return p;
    }

    private Sale sale(long id, User employee, String customer, LocalDateTime date, Object... items) {
        Sale s = Sale.builder().salesId(id).employee(employee).customerName(customer).date(date).build();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.length; i += 3) {
            BigDecimal price = new BigDecimal(items[i + 2].toString());
            int qty = (int) items[i + 1];
            s.addDetail(SaleDetail.builder().product((Product) items[i]).amount(qty).unitPrice(price).build());
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        s.setTotal(total);
        return s;
    }

    private void givenSales(List<Sale> current, List<Sale> previous) {
        when(repo.findForReport(any(), any())).thenAnswer(inv -> {
            LocalDateTime from = inv.getArgument(0);
            return from.toLocalDate().equals(FROM) ? current : previous;
        });
    }

    @Test
    void totalsAverageAndUnits() {
        givenSales(List.of(
                sale(1, ana, "Juan", FROM.atTime(9, 0), phone, 1, "1000.00"),
                sale(2, luis, "", FROM.plusDays(2).atTime(15, 0), case_, 3, "50.00", phone, 1, "1000.00")
        ), List.of());

        SalesSummaryDto summary = service.summarize(FROM, TO);

        assertEquals(new BigDecimal("2150.00"), summary.current().total());
        assertEquals(2, summary.current().transactions());
        assertEquals(new BigDecimal("1075.00"), summary.current().averageTicket());
        assertEquals(5, summary.current().units());
    }

    @Test
    void comparesAgainstPreviousPeriodOfSameLength() {
        givenSales(
                List.of(sale(1, ana, "A", FROM.atTime(9, 0), phone, 1, "150.00")),
                List.of(sale(2, ana, "B", FROM.minusDays(2).atTime(9, 0), phone, 1, "100.00")));

        SalesSummaryDto summary = service.summarize(FROM, TO);

        assertEquals(LocalDate.of(2026, 3, 7), summary.previousFrom());
        assertEquals(LocalDate.of(2026, 3, 9), summary.previousTo());
        assertEquals(new BigDecimal("50.0"), summary.totalChangePct());
        assertEquals(new BigDecimal("0.0"), summary.transactionsChangePct());
    }

    @Test
    void changeIsNullWhenPreviousPeriodHadNoSales() {
        givenSales(List.of(sale(1, ana, "A", FROM.atTime(9, 0), phone, 1, "150.00")), List.of());

        assertNull(service.summarize(FROM, TO).totalChangePct());
    }

    @Test
    void decreaseGivesNegativePercentage() {
        givenSales(
                List.of(sale(1, ana, "A", FROM.atTime(9, 0), phone, 1, "50.00")),
                List.of(sale(2, ana, "B", FROM.minusDays(1).atTime(9, 0), phone, 1, "200.00")));

        assertEquals(new BigDecimal("-75.0"), service.summarize(FROM, TO).totalChangePct());
    }

    @Test
    void byDayIncludesDaysWithoutSalesInZero() {
        givenSales(List.of(sale(1, ana, "A", FROM.plusDays(1).atTime(10, 0), phone, 1, "80.00")), List.of());

        var days = service.summarize(FROM, TO).byDay();

        assertEquals(3, days.size());
        assertEquals(BigDecimal.ZERO.setScale(2), days.get(0).total());
        assertEquals(new BigDecimal("80.00"), days.get(1).total());
        assertEquals(1, days.get(1).transactions());
        assertEquals(0, days.get(2).transactions());
    }

    @Test
    void employeesAreRankedByTotal() {
        givenSales(List.of(
                sale(1, ana, "A", FROM.atTime(9, 0), phone, 1, "100.00"),
                sale(2, luis, "B", FROM.atTime(10, 0), phone, 1, "500.00"),
                sale(3, ana, "C", FROM.atTime(11, 0), case_, 1, "50.00")), List.of());

        var ranking = service.summarize(FROM, TO).byEmployee();

        assertEquals("luis", ranking.get(0).userName());
        assertEquals("Luis Gómez", ranking.get(0).name());
        assertEquals(new BigDecimal("150.00"), ranking.get(1).total());
        assertEquals(2, ranking.get(1).transactions());
    }

    @Test
    void topProductsAreRankedByUnitsAndLimitedToTen() {
        List<Sale> sales = new java.util.ArrayList<>();
        sales.add(sale(1, ana, "A", FROM.atTime(9, 0), case_, 5, "10.00", phone, 1, "900.00"));
        for (int i = 0; i < 12; i++) {
            sales.add(sale(10 + i, ana, "A", FROM.atTime(10, i), product(100L + i, "P" + i), 1, "1.00"));
        }
        givenSales(sales, List.of());

        var top = service.summarize(FROM, TO).topProducts();

        assertEquals(10, top.size());
        assertEquals("Funda", top.get(0).name());
        assertEquals(5, top.get(0).units());
        assertEquals(new BigDecimal("50.00"), top.get(0).revenue());
    }

    @Test
    void salesDetailIsNewestFirstWithPlaceholderForMissingCustomer() {
        givenSales(List.of(
                sale(1, ana, "Juan", FROM.atTime(9, 0), phone, 1, "100.00"),
                sale(2, ana, "", FROM.atTime(16, 0), case_, 2, "10.00")), List.of());

        var rows = service.summarize(FROM, TO).sales();

        assertEquals(2L, rows.get(0).id());
        assertEquals("-", rows.get(0).customer());
        assertEquals("Funda x2", rows.get(0).products());
        assertEquals(2, rows.get(0).units());
    }

    @Test
    void rejectsInvertedAndTooLongRanges() {
        assertThrows(ResponseStatusException.class, () -> service.summarize(TO, FROM));
        assertThrows(ResponseStatusException.class, () -> service.summarize(FROM, FROM.plusDays(366)));
        givenSales(List.of(), List.of());
        assertDoesNotThrow(() -> service.summarize(FROM, FROM.plusDays(365)));
    }

    @Test
    void emptyPeriodHasZeroesAndNoErrors() {
        givenSales(List.of(), List.of());

        SalesSummaryDto summary = service.summarize(FROM, TO);

        assertEquals(0, summary.current().transactions());
        assertEquals(BigDecimal.ZERO.setScale(2), summary.current().averageTicket());
        assertTrue(summary.byEmployee().isEmpty());
        assertTrue(summary.topProducts().isEmpty());
        assertEquals(3, summary.byDay().size());
    }
}
