package com.eczam.ai;

import com.eczam.ai.dto.ChatDtos.ChatRequest;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.Inputs;
import io.micrometer.core.instrument.Counter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Tag(name = "AI Assistant", description = "RAG-based medication assistant grounded strictly in drug leaflets. Answers are streamed via Server-Sent Events.")
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final RagService rag;
    private final Counter aiQueriesCounter;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(RagService rag,
                          @Qualifier("aiQueriesCounter") Counter aiQueriesCounter) {
        this.rag = rag;
        this.aiQueriesCounter = aiQueriesCounter;
    }

    @Operation(
        summary = "Ask the AI assistant (streaming)",
        description = """
            Sends a question to the RAG-based AI assistant and streams the response as Server-Sent Events.

            **SSE event types:**
            - `token` — a chunk of the assistant's answer text (append these to build the full response)
            - `citation` — a leaflet section used as a source (e.g. `"4. Possible side effects"`)
            - `done` — final event with `{ "grounded": true/false }` — `false` means the question couldn't be answered from the leaflets

            **Guardrails:** The assistant only answers from retrieved leaflet passages. It will not speculate beyond what the leaflets say and will recommend consulting a pharmacist when uncertain.

            **Rate limit:** 20 requests/minute per IP.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                     description = "SSE stream — events: `token`, `citation`, `done`",
                     content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                        schema = @Schema(type = "string", example = "event: token\\ndata: Ibuprofen\\n\\n"))),
        @ApiResponse(responseCode = "422", description = "Validation failed", content = @Content),
        @ApiResponse(responseCode = "429", description = "Rate limited", content = @Content)
    })
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@CurrentUser UUID userId, @Valid @RequestBody ChatRequest req) {
        aiQueriesCounter.increment();
        SseEmitter emitter = new SseEmitter(60_000L);
        UUID medId = Inputs.uuidOrNull(req.medicationId(), "medicationId");
        List<String> history = req.history() == null ? List.of() : req.history();

        executor.submit(() -> {
            try {
                boolean grounded = rag.answer(req.message(), medId, history,
                        token -> sendQuietly(emitter, "token", token),
                        cite  -> sendQuietly(emitter, "citation", cite.section()));
                emitter.send(SseEmitter.event().name("done").data("{\"grounded\":" + grounded + "}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendQuietly(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ignored) { /* client disconnected */ }
    }
}
