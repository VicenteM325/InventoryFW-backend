package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.StockAlertResponseDto;
import com.vm325.inventory_back.entities.StockAlert;

import java.util.List;

public interface StockAlertService {
    List<StockAlertResponseDto> findPending();
    StockAlertResponseDto attend(Long id);

    /** Representación de una alerta para la API. */
    StockAlertResponseDto toResponse(StockAlert alert);
}
