package com.ignteq.tcagent;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Minimal Anthropic Messages API client (no SDK, JDK HttpClient only). */
public class Anthropic implements ApiClient {

    private static final String URL = "https://api.anthropic.com/v1/messages";
    private static final String VERSION = "2023-06-01";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public Map<String, Object> body(Config c, List<Block> blocks, String task) {
        List<Object> content = new ArrayList<>();
        for (Block b : blocks) content.add(toContentBlock(b));

        Map<String, Object> taskBlock = Json.obj();
        taskBlock.put("type", "text");
        taskBlock.put("text", task);
        content.add(taskBlock);

        Map<String, Object> msg = Json.obj();
        msg.put("role", "user");
        msg.put("content", content);

        Map<String, Object> req = Json.obj();
        req.put("model", c.model);
        req.put("max_tokens", c.maxTokens);
        req.put("system", Prompt.SYSTEM);
        req.put("messages", List.of(msg));
        return req;
    }

    private static Map<String, Object> toContentBlock(Block block) {
        Map<String, Object> b = Json.obj();
        switch (block.kind) {
            case TEXT -> {
                b.put("type", "text");
                b.put("text", block.text);
            }
            case IMAGE -> {
                Map<String, Object> src = Json.obj();
                src.put("type", "base64");
                src.put("media_type", block.mediaType);
                src.put("data", Base64.getEncoder().encodeToString(block.data));
                b.put("type", "image");
                b.put("source", src);
            }
            case PDF -> {
                Map<String, Object> src = Json.obj();
                src.put("type", "base64");
                src.put("media_type", "application/pdf");
                src.put("data", Base64.getEncoder().encodeToString(block.data));
                b.put("type", "document");
                b.put("source", src);
            }
        }
        return b;
    }

    /** Sends the request, retrying on 429/5xx. Returns the parsed response object. */
    @Override
    public Object send(Config c, Map<String, Object> requestBody) throws IOException, InterruptedException {
        String payload = Json.write(requestBody);
        IOException last = null;

        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofMinutes(10))
                    .header("content-type", "application/json")
                    .header("x-api-key", c.apiKey)
                    .header("anthropic-version", VERSION)
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

    /** Concatenates all text blocks of the response. */
    @Override
    public String text(Object response) {
        StringBuilder sb = new StringBuilder();
        for (Object block : Json.asList(Json.asMap(response).get("content"))) {
            if ("text".equals(Json.str(block, "type"))) sb.append(Json.str(block, "text"));
        }
        return sb.toString();
    }

    @Override
    public boolean truncated(Object response) {
        return "max_tokens".equals(Json.str(response, "stop_reason"));
    }

    @Override
    public String usage(Object response) {
        Object u = Json.asMap(response).get("usage");
        if (u == null) return "";
        return "tokens in=" + Json.str(u, "input_tokens") + " out=" + Json.str(u, "output_tokens");
    }
}
