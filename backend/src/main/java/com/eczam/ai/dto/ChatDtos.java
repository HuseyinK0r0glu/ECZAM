package com.eczam.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class ChatDtos {
    public record ChatRequest(@NotBlank String message, String medicationId, List<String> history) {}
    private ChatDtos() {}
}
