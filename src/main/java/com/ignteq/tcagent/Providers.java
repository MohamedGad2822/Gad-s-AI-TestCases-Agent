package com.ignteq.tcagent;

/** Picks the API client for the config's resolved provider. */
public class Providers {
    public static ApiClient forConfig(Config c) {
        return switch (c.provider) {
            case ANTHROPIC -> new Anthropic();
            case GEMINI -> new Gemini();
            case OPENAI -> new ChatCompletions(
                    "https://api.openai.com/v1/chat/completions", "max_completion_tokens", true);
            case DEEPSEEK -> new ChatCompletions(
                    "https://api.deepseek.com/chat/completions", "max_tokens", false);
            case XAI -> new ChatCompletions(
                    "https://api.x.ai/v1/chat/completions", "max_tokens", false);
            case OPENROUTER -> new ChatCompletions(
                    "https://openrouter.ai/api/v1/chat/completions", "max_tokens", false);
        };
    }
}
