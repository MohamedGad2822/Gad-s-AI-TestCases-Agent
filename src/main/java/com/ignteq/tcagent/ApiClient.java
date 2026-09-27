package com.ignteq.tcagent;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** One model backend: Anthropic, OpenAI, or Gemini. Each speaks its own wire format. */
public interface ApiClient {

    /** Builds the provider-specific request body from provider-neutral evidence blocks. */
    Map<String, Object> body(Config c, List<Block> blocks, String task);

    /** Sends the request, retrying on rate limit / server errors. Returns the parsed JSON response. */
    Object send(Config c, Map<String, Object> requestBody) throws IOException, InterruptedException;

    /** Concatenates the response's text output. */
    String text(Object response);

    /** True if the response was cut off for running out of output budget. */
    boolean truncated(Object response);

    /** Human-readable token usage line for the summary log. */
    String usage(Object response);
}
