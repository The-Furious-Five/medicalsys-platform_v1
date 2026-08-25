package com.medicalsys.documental.api.dto;

import java.util.List;

public record DocumentPageResponse(
        List<DocumentSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
