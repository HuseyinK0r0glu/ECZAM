package com.eczam.ai;

import com.eczam.ai.dto.ChatDtos.ChatRequest;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.Inputs;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final RagService rag;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(RagService rag) { this.rag = rag; }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@CurrentUser UUID userId, @Valid @RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(60_000L);
        UUID medId = Inputs.uuidOrNull(req.medicationId(), "medicationId");
        List<String> history = req.history() == null ? List.of() : req.history();

        executor.submit(() -> {
            try {
                boolean grounded = rag.answer(req.message(), medId, history,
                        token -> sendQuietly(emitter, "token", token),
                        cite -> sendQuietly(emitter, "citation", cite.section()));
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
