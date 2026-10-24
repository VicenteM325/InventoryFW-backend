package com.vm325.inventory_back.repositories;

import com.vm325.inventory_back.entities.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findTop10ByOrderByDateDesc();
    List<Sale> findByDateBetweenOrderByDateAsc(LocalDateTime from, LocalDateTime to);

    /**
     * Ventas del periodo con empleado, detalles y productos ya cargados: los reportes
     * recorren todo, y sin el fetch cada venta dispararía varias consultas extra.
     */
    @Query("select distinct s from Sale s join fetch s.employee left join fetch s.details d "
            + "left join fetch d.product where s.date between :from and :to order by s.date asc")
    List<Sale> findForReport(LocalDateTime from, LocalDateTime to);
}