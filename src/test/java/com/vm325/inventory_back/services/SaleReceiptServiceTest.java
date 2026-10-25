package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.SaleResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SaleReceiptServiceTest {

    private MailService mail;
    private ReceiptEmailLimiter limiter;
    private SaleReceiptService service;
    private final SaleResponseDto sale = SaleResponseDto.builder().salesId(7L).build();

    @BeforeEach
    void setUp() {
        mail = mock(MailService.class);
        limiter = mock(ReceiptEmailLimiter.class);
        when(limiter.tryAcquire(any(), any())).thenReturn(true);
        service = new SaleReceiptService(mail, limiter);
    }

    @Test
    void withoutEmailNothingIsSent() {
        assertEquals("OMITIDO", service.sendReceipt(sale, null, "emp1"));
        assertEquals("OMITIDO", service.sendReceipt(sale, "  ", "emp1"));
        verifyNoInteractions(mail);
    }

    @Test
    void sendsTrimmedAddressAndReportsSent() {
        assertEquals("ENVIADO", service.sendReceipt(sale, " ana@test.com ", "emp1"));
        verify(mail).sendSaleReceipt(eq("ana@test.com"), eq(sale));
    }

    @Test
    void mailFailureIsReportedNotThrown() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(mail).sendSaleReceipt(any(), any());

        assertEquals("NO_ENVIADO", service.sendReceipt(sale, "ana@test.com", "emp1"));
    }

    @Test
    void limitReachedSkipsSending() {
        when(limiter.tryAcquire("emp1", "ana@test.com")).thenReturn(false);

        assertEquals("NO_ENVIADO", service.sendReceipt(sale, "ana@test.com", "emp1"));
        verifyNoInteractions(mail);
    }
}
