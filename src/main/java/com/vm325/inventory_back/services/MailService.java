package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.SaleResponseDto;

public interface MailService {

    /** Código de verificación del segundo paso del login. */
    void sendLoginCode(String to, String name, String code, int validMinutes);

    /** Código para confirmar la activación de 2FA (verifica además que el correo es del usuario). */
    void sendEnableTwoFactorCode(String to, String name, String code, int validMinutes);

    /** Enlace de recuperación de contraseña. */
    void sendPasswordReset(String to, String name, String link, int validMinutes);

    /** Recibo de una venta, para el correo que dejó el cliente. */
    void sendSaleReceipt(String to, SaleResponseDto sale);

    /** Datos de un pedido de reposición a un proveedor. */
    record SupplierOrder(String to, String supplierName, String productName, String barcode,
                         int quantity, int currentStock, int minStock) {
    }

    /** Correo ya armado (destinatario, asunto y HTML), tal como se enviará. */
    record RenderedMail(String to, String subject, String html) {
    }

    /** Arma el correo de pedido sin enviarlo: sirve de vista previa. */
    RenderedMail previewSupplierOrder(SupplierOrder order);

    /** Envía el pedido al proveedor; las respuestas llegan al correo del negocio (Reply-To). */
    void sendSupplierOrder(SupplierOrder order);
}
