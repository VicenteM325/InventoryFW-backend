package com.vm325.inventory_back.reports;

public interface ReportWriter {

    ReportFormat getFormat();

    byte[] write(ReportDocument document);
}
