# Pruebas empíricas de validaciones de negocio

Fecha: 2026-10-24 · Backend en `integration/2fa` · PostgreSQL 15 real (Docker). **21 de 21 comprobaciones pasaron.**

| Área | Resultado verificado |
|---|---|
| Productos | editar con el código de barras de otro producto → 409 (antes 500); conservar el propio → 200; nombre de 101 caracteres → 400 |
| Ventas | el precio enviado por el cliente (0.01) se ignora y se cobra el del catálogo (total 201.00); `unitPrice` es opcional; stock insuficiente → 409; el mismo producto repetido en la venta suma cantidades; producto dado de baja → 409 |
| Concurrencia | 10 ventas simultáneas de 1 unidad sobre un producto con stock 3 → exactamente 3 exitosas, 7 conflictos 409 y stock final 0 (antes podía quedar negativo) |
| Usuarios | un admin no puede desactivarse ni quitarse el rol a sí mismo (409); con dos admins activos sí se puede desactivar al otro; un empleado recibe 403 al crear o desactivar usuarios |
| Notificaciones | un empleado lista solo las de su rol o generales; marcar una inexistente → 404 |

No se probó contra el navegador real ni contra Mailtrap (ver `mailtrap-test-log.md`).

---

# Reportes: análisis y exportación (2026-10-24)

78 pruebas unitarias del backend pasan (10 del análisis de ventas, 9 de los exportadores y el resto
previas). Con 103 ventas sembradas en 60 días, `/api/reports/sales/summary` coincidió con SQL
directo en total (Q 291,376.00), transacciones (69), unidades (248), periodo anterior
(Q 132,133.00 / 34) y total por empleado (Q 141,067.00).

| Prueba | Resultado |
|---|---|
| `GET /api/reports/sales/summary` con rango invertido | 400 |
| Rango de más de 366 días | 400 |
| Sin sesión | 401 |
| `format=DOC` en la exportación | 400 |
| PDF y XLSX de ventas e inventario | 200, `Content-Type` y nombre de archivo correctos |
| PDF de 120 ventas | paginado con "Página X de Y"; texto ajustado en varias líneas |
| XLSX | valores nativos (fechas, moneda), autofiltro, encabezado fijo, fórmula SUM en totales y gráfico; XML bien formado |

Cambio de comportamiento: el reporte de inventario ahora excluye los productos dados de baja.
No se abrió el XLSX en Excel ni LibreOffice (no disponibles en este equipo): se verificó leyéndolo
con Apache POI y validando su XML.
