package com.vm325.inventory_back.controllers;

import com.vm325.inventory_back.reports.InventoryAnalyticsService;
import com.vm325.inventory_back.reports.InventoryReportService;
import com.vm325.inventory_back.reports.ReportFormat;
import com.vm325.inventory_back.reports.SalesAnalyticsService;
import com.vm325.inventory_back.reports.SalesReportService;
import com.vm325.inventory_back.reports.dto.InventorySummaryDto;
import com.vm325.inventory_back.reports.dto.SalesSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// Reportes de ventas e inventario, exportables en Excel y PDF (RF-07).
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class ReportController {

    private static final DateTimeFormatter FILENAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesReportService salesReportService;
    private final InventoryReportService inventoryReportService;
    private final SalesAnalyticsService salesAnalyticsService;
    private final InventoryAnalyticsService inventoryAnalyticsService;

    /** Datos para la vista en pantalla. Por defecto, los últimos 30 días. */
    @GetMapping("/sales/summary")
    public ResponseEntity<SalesSummaryDto> salesSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(29);
        return ResponseEntity.ok(salesAnalyticsService.summarize(effectiveFrom, effectiveTo));
    }

    @GetMapping("/inventory/summary")
    public ResponseEntity<InventorySummaryDto> inventorySummary() {
        return ResponseEntity.ok(inventoryAnalyticsService.summarize());
    }

    @GetMapping("/sales")
    public ResponseEntity<byte[]> salesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "PDF") String format,
            Authentication authentication) {

        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(29);

        ReportFormat reportFormat = parseFormat(format);
        byte[] report = salesReportService.generate(effectiveFrom, effectiveTo, reportFormat, authentication.getName());

        String filename = "reporte_ventas_" + effectiveFrom.format(FILENAME_DATE)
                + "_" + effectiveTo.format(FILENAME_DATE) + "." + reportFormat.getFileExtension();

        return respond(report, reportFormat, filename);
    }

    @GetMapping("/inventory")
    public ResponseEntity<byte[]> inventoryReport(@RequestParam(defaultValue = "PDF") String format,
                                                  Authentication authentication) {
        ReportFormat reportFormat = parseFormat(format);
        byte[] report = inventoryReportService.generate(reportFormat, authentication.getName());

        String filename = "reporte_inventario_" + LocalDate.now().format(FILENAME_DATE)
                + "." + reportFormat.getFileExtension();

        return respond(report, reportFormat, filename);
    }

    private ResponseEntity<byte[]> respond(byte[] report, ReportFormat format, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(report);
    }

    private ReportFormat parseFormat(String format) {
        try {
            return ReportFormat.valueOf(format.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato no soportado: " + format + " (use PDF o XLSX)");
        }
    }
}
