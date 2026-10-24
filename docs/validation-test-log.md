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
