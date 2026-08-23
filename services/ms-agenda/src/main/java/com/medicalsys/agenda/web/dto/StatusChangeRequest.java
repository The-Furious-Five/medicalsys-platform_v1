package com.medicalsys.agenda.web.dto;

import com.medicalsys.agenda.domain.StatusChangeOrigin;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StatusChangeRequest(
        @Size(max = 300) String reason,
        UUID userAccountPublicId,
        StatusChangeOrigin origin) {
}
