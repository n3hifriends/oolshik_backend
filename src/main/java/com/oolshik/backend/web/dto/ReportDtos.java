// src/main/java/com/oolshik/backend/web/dto/ReportDtos.java
package com.oolshik.backend.web.dto;

import com.oolshik.backend.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ReportDtos {
    public record CreateRequest(
            UUID taskId,
            UUID targetUserId,
            @NotNull ReportReason reason,
            @Size(max = 1000) String text
    ) {}
    public record CreateResponse(UUID id) {}
}