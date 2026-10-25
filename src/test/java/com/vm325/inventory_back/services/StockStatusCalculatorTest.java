package com.vm325.inventory_back.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockStatusCalculatorTest {

    private final StockStatusCalculator calculator = new StockStatusCalculator();

    @Test
    void stockEqualToMinimumIsAlreadyLow() {
        // Con el mínimo en 3, la alerta salta al llegar a 3 y no hasta bajar a 2.
        assertEquals("DISPONIBLE", calculator.resolve(4, 3));
        assertEquals("STOCK_BAJO", calculator.resolve(3, 3));
        assertEquals("STOCK_BAJO", calculator.resolve(2, 3));
        assertEquals("STOCK_BAJO", calculator.resolve(1, 3));
    }

    @Test
    void zeroOrLessIsSoldOut() {
        assertEquals("AGOTADO", calculator.resolve(0, 3));
        assertEquals("AGOTADO", calculator.resolve(0, 0));
        assertEquals("AGOTADO", calculator.resolve(-1, 3));
    }

    @Test
    void positiveStockWithZeroMinimumIsAvailable() {
        assertEquals("DISPONIBLE", calculator.resolve(1, 0));
    }

    @Test
    void alertRuleIsInclusive() {
        assertTrue(StockStatusCalculator.isAtOrBelowMinimum(3, 3));
        assertTrue(StockStatusCalculator.isAtOrBelowMinimum(2, 3));
        assertFalse(StockStatusCalculator.isAtOrBelowMinimum(4, 3));
    }
}
