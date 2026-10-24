package com.medicalsys.facturacion.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VoidInvoiceRequest(@NotNull @Min(1) @Max(4) Integer reasonCode) {
}
