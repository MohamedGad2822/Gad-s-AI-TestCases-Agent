package com.ignteq.tcagent;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * OpenAI-style Chat Completions client, shared by every provider that speaks this
 * (by far the most widely mirrored) wire format: OpenAI itself, DeepSeek, and
 * OpenRouter (a single gateway to hundreds of other models, model id "author/slug").
 */
public class ChatCompletions implements ApiClient {

    private final String url;
    private final String maxTokensField; // OpenAI moved to "max_completion_tokens"; the rest kept "max_tokens"
    private final boolean supportsPdf;   // PDF-as-file input is an OpenAI-specific extension

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public ChatCompletions(String url, String maxTokensField, boolean supportsPdf) {
        this.url = url;
        this.maxTokensField = maxTokensField;
        this.supportsPdf = supportsPdf;
    }

    @Override
    public Map<String, Object> body(Config c, List<Block> blocks, String task) {
        List<Object> content = new ArrayList<>();
        for (Block b : blocks) {
            Object part = toContentPart(b);
            if (part != null) content.add(part);
        }

        Map<String, Object> taskPart = Json.obj();
        taskPart.put("type", "text");
        taskPart.put("text", task);
        content.add(taskPart);

        Map<String, Object> system = Json.obj();
        system.put("role", "system");
        system.put("content", Prompt.SYSTEM);

        Map<String, Object> user = Json.obj();
        user.put("role", "user");
        user.put("content", content);

        Map<String, Object> req = Json.obj();
        req.put("model", c.model);
        req.put("messages", List.of(system, user));
        req.put(maxTokensField, c.maxTokens);
        return req;
    }

    /** Null means "drop this block" - e.g. a PDF on a provider that has no file input. */
    private Object toContentPart(Block block) {
        switch (block.kind) {
            case TEXT -> {
                Map<String, Object> b = Json.obj();
                b.put("type", "text");
                b.put("text", block.text);
                return b;
            }
            case IMAGE -> {
                Map<String, Object> url = Json.obj();
                url.put("url", dataUrl(block.mediaType, block.data));
                Map<String, Object> b = Json.obj();
                b.put("type", "image_url");
                b.put("image_url", url);
                return b;
            }
            case PDF -> {
                if (!supportsPdf) return null;
                Map<String, Object> file = Json.obj();
                file.put("filename", "document.pdf");
                file.put("file_data", dataUrl("application/pdf", block.data));
                Map<String, Object> b = Json.obj();
                b.put("type", "file");
                b.put("file", file);
                return b;
            }
        }
        return null;
    }

    private static String dataUrl(String mediaType, byte[] data) {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(data);
    }

    /** Sends the request, retrying on 429/5xx. Returns the parsed response object. */
    @Override
    public Object send(Config c, Map<String, Object> requestBody) throws IOException, InterruptedException {
        String payload = Json.write(requestBody);
        IOException last = null;

        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(10))
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + c.apiKey)
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

    private static Object firstChoice(Object response) {
        List<Object> choices = Json.asList(Json.asMap(response).get("choices"));
        return choices.isEmpty() ? null : choices.get(0);
    }

    @Override
    public String text(Object response) {
        Object choice = firstChoice(response);
        if (choice == null) return "";
        return Json.str(Json.asMap(choice).get("message"), "content");
    }

    @Override
    public boolean truncated(Object response) {
        Object choice = firstChoice(response);
        return choice != null && "length".equals(Json.str(choice, "finish_reason"));
    }

    @Override
    public String usage(Object response) {
        Object u = Json.asMap(response).get("usage");
        if (u == null) return "";
        return "tokens in=" + Json.str(u, "prompt_tokens") + " out=" + Json.str(u, "completion_tokens");
    }
}
