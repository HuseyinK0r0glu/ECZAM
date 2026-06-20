package com.eczam.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class EmbeddingClient {

    private final RestClient http;
    private final String model;

    public EmbeddingClient(@Value("${OPENAI_API_KEY:}") String apiKey,
                           @Value("${eczam.ai.embedding-model}") String model) {
        this.model = model;
        this.http = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        Map<String, Object> body = http.post().uri("/embeddings")
                .body(Map.of("model", model, "input", text))
                .retrieve().body(Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        List<Number> vec = (List<Number>) data.get(0).get("embedding");
        float[] out = new float[vec.size()];
        for (int i = 0; i < vec.size(); i++) out[i] = vec.get(i).floatValue();
        return out;
    }
}
