package com.ignteq.tcagent;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Minimal Google Gemini generateContent API client (no SDK, JDK HttpClient only). */
public class Gemini implements ApiClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static String url(String model) {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
    }

    @Override
    public Map<String, Object> body(Config c, List<Block> blocks, String task) {
        List<Object> parts = new ArrayList<>();
        for (Block b : blocks) parts.add(toPart(b));

        Map<String, Object> taskPart = Json.obj();
        taskPart.put("text", task);
        parts.add(taskPart);

        Map<String, Object> content = Json.obj();
        content.put("role", "user");
        content.put("parts", parts);

        Map<String, Object> systemText = Json.obj();
        systemText.put("text", Prompt.SYSTEM);
        Map<String, Object> systemInstruction = Json.obj();
        systemInstruction.put("parts", List.of(systemText));

        Map<String, Object> generationConfig = Json.obj();
        generationConfig.put("maxOutputTokens", c.maxTokens);

        Map<String, Object> req = Json.obj();
        req.put("system_instruction", systemInstruction);
        req.put("contents", List.of(content));
        req.put("generationConfig", generationConfig);
        return req;
    }

    private static Map<String, Object> toPart(Block block) {
        Map<String, Object> p = Json.obj();
        switch (block.kind) {
            case TEXT -> p.put("text", block.text);
            case IMAGE, PDF -> {
                Map<String, Object> inline = Json.obj();
                inline.put("mime_type", block.mediaType);
                inline.put("data", Base64.getEncoder().encodeToString(block.data));
                p.put("inline_data", inline);
            }
        }
        return p;
    }

    /** Sends the request, retrying on 429/5xx. Returns the parsed response object. */
    @Override
    public Object send(Config c, Map<String, Object> requestBody) throws IOException, InterruptedException {
        String payload = Json.write(requestBody);
        IOException last = null;

        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url(c.model)))
                    .timeout(Duration.ofMinutes(10))
                    .header("content-type", "application/json")
                    .header("x-goog-api-key", c.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();

            if (code == 200) return Json.parse(res.body());

            String snippet = res.body() == null ? "" :
                    res.body().substring(0, Math.min(400, res.body().length()));

            if (code == 429 || code >= 500) {
                last = new IOException("HTTP " + code + ": " + snippet);
                long wait = (long) Math.pow(2, attempt) * 1000L;
                System.err.println("  ! HTTP " + code + " - retrying in " + (wait / 1000) + "s (attempt "
                        + attempt + "/4)");
                Thread.sleep(wait);
                continue;
            }
            throw new IOException("API error HTTP " + code + ": " + snippet);
        }
        throw last != null ? last : new IOException("request failed");
    }

    /** Concatenates all text parts of the first candidate. */
    @Override
    public String text(Object response) {
        List<Object> candidates = Json.asList(Json.asMap(response).get("candidates"));
        if (candidates.isEmpty()) return "";
        Object content = Json.asMap(candidates.get(0)).get("content");
        StringBuilder sb = new StringBuilder();
        for (Object part : Json.asList(Json.asMap(content).get("parts"))) {
            sb.append(Json.str(part, "text"));
        }
        return sb.toString();
    }

    @Override
    public boolean truncated(Object response) {
        List<Object> candidates = Json.asList(Json.asMap(response).get("candidates"));
        if (candidates.isEmpty()) return false;
        return "MAX_TOKENS".equals(Json.str(candidates.get(0), "finishReason"));
    }

    @Override
    public String usage(Object response) {
        Object u = Json.asMap(response).get("usageMetadata");
        if (u == null) return "";
        return "tokens in=" + Json.str(u, "promptTokenCount") + " out=" + Json.str(u, "candidatesTokenCount");
    }
}
