package com.ignteq.tcagent;

/** Which model backend a run talks to, plus that backend's sane default model. */
public enum Provider {
    ANTHROPIC("claude-sonnet-5"),
    OPENAI("gpt-5.5"),
    GEMINI("gemini-3.8-flash"),
    DEEPSEEK("deepseek-v4-pro"),
    XAI("grok-4.7"),
    OPENROUTER("anthropic/claude-sonnet-5");

    public final String defaultModel;

    Provider(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    /**
     * Guesses the provider from the shape of the API key, so pasting most of these
     * "just works" with no extra flag. More specific prefixes are checked before the
     * generic "sk-" one they'd otherwise also match. OpenAI and DeepSeek keys both look
     * like plain "sk-..." with no reliable distinguishing shape, so that generic case
     * falls back to OpenAI - use --provider deepseek (CLI) or the provider dropdown
     * (GUI) to be explicit.
     */
    public static Provider detect(String apiKey) {
        String k = apiKey == null ? "" : apiKey.trim();
        if (k.startsWith("sk-ant-")) return ANTHROPIC;
        if (k.startsWith("sk-or-")) return OPENROUTER;
        if (k.startsWith("xai-")) return XAI;
        if (k.startsWith("AIza")) return GEMINI;
        if (k.startsWith("sk-")) return OPENAI;
        return ANTHROPIC;
    }

    public static Provider parse(String name) {
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown provider: " + name
                    + " (want anthropic, openai, gemini, deepseek, xai, or openrouter)");
        }
    }

    public String label() {
        return switch (this) {
            case ANTHROPIC -> "Anthropic";
            case OPENAI -> "OpenAI";
            case GEMINI -> "Gemini";
            case DEEPSEEK -> "DeepSeek";
            case XAI -> "xAI (Grok)";
            case OPENROUTER -> "OpenRouter";
        };
    }
}
