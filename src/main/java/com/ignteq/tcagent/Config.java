package com.ignteq.tcagent;

import java.nio.file.*;
import java.util.*;

/** Command-line configuration. */
public class Config {

    public Path in = Paths.get("inputs");
    public Path out = Paths.get("out");
    public String module = "";
    public String code = "";
    public int start = 1;
    public int projectNumber = 24;
    public String apiKey = defaultApiKey();
    public Provider provider = Provider.detect(apiKey);
    public String model = env("TC_MODEL", provider.defaultModel);
    public int maxTokens = 16000;
    public String notes = "";
    public int targetCount = 0;      // 0 = model decides
    public boolean dryRun = false;
    public boolean debug = false;

    public static Config parse(String[] args) {
        Config c = new Config();
        String explicitModel = null;
        String explicitProvider = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--in":        c.in = Paths.get(next(args, ++i, a)); break;
                case "--out":       c.out = Paths.get(next(args, ++i, a)); break;
                case "--module":    c.module = next(args, ++i, a); break;
                case "--code":      c.code = next(args, ++i, a); break;
                case "--start":     c.start = Integer.parseInt(next(args, ++i, a)); break;
                case "--project":   c.projectNumber = Integer.parseInt(next(args, ++i, a)); break;
                case "--model":     explicitModel = next(args, ++i, a); break;
                case "--provider":  explicitProvider = next(args, ++i, a); break;
                case "--max-tokens":c.maxTokens = Integer.parseInt(next(args, ++i, a)); break;
                case "--count":     c.targetCount = Integer.parseInt(next(args, ++i, a)); break;
                case "--notes":     c.notes = next(args, ++i, a); break;
                case "--notes-file":
                    try {
                        c.notes = new String(Files.readAllBytes(Paths.get(next(args, ++i, a))),
                                java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("cannot read notes file: " + e.getMessage());
                    }
                    break;
                case "--key":       c.apiKey = next(args, ++i, a); break;
                case "--dry-run":   c.dryRun = true; break;
                case "--debug":     c.debug = true; break;
                case "-h":
                case "--help":      usage(); System.exit(0); break;
                default:
                    throw new IllegalArgumentException("unknown option: " + a);
            }
        }
        if (c.module.isEmpty()) throw new IllegalArgumentException("--module is required (e.g. --module Forms)");
        if (c.code.isEmpty()) c.code = deriveCode(c.module);
        if (!c.dryRun && c.apiKey.isEmpty())
            throw new IllegalArgumentException(
                    "no API key: set ANTHROPIC_API_KEY / OPENAI_API_KEY / GEMINI_API_KEY / "
                            + "DEEPSEEK_API_KEY / XAI_API_KEY / OPENROUTER_API_KEY, or pass --key");

        // Provider is re-resolved here (not just at field init) so a --key that
        // overrides the env default still picks the right backend.
        c.provider = explicitProvider != null ? Provider.parse(explicitProvider) : Provider.detect(c.apiKey);
        c.model = explicitModel != null ? explicitModel : env("TC_MODEL", c.provider.defaultModel);
        return c;
    }

    static String deriveCode(String module) {
        StringBuilder sb = new StringBuilder();
        for (String w : module.trim().split("[^A-Za-z0-9]+")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0)));
        }
        String s = sb.toString();
        if (s.length() < 2) {
            s = module.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            if (s.length() > 3) s = s.substring(0, 3);
        }
        return s.length() > 5 ? s.substring(0, 5) : (s.isEmpty() ? "TC" : s);
    }

    private static String next(String[] args, int i, String flag) {
        if (i >= args.length) throw new IllegalArgumentException("missing value for " + flag);
        return args[i];
    }

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return (v == null || v.isEmpty()) ? dflt : v;
    }

    /** Anthropic first (matches the tool's original behaviour), then the rest in no particular order. */
    private static String defaultApiKey() {
        for (String k : List.of("ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY", "GOOGLE_API_KEY",
                "DEEPSEEK_API_KEY", "XAI_API_KEY", "OPENROUTER_API_KEY")) {
            String v = env(k, "");
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    public static void usage() {
        System.out.println(String.join("\n",
            "tc-agent — screenshots + docs -> QA test cases (Anthropic / OpenAI / Gemini / DeepSeek / xAI / OpenRouter)",
            "",
            "Usage:",
            "  java -jar tc-agent.jar --in <folder> --module <Prefix> [options]",
            "",
            "Required:",
            "  --in <dir>         folder with screenshots (.png/.jpg/.webp/.gif),",
            "                     PDFs, and notes/BRDs (.md/.txt/.csv/.json/.html)",
            "  --module <name>    title prefix, e.g. Forms / Agenda / Permissions",
            "",
            "Options:",
            "  --out <dir>        output folder                 (default: out)",
            "  --code <ID>        internal ID prefix            (default: derived, e.g. FORMS -> F)",
            "  --start <n>        first case number             (default: 1)",
            "  --count <n>        target number of cases        (default: model decides)",
            "  --notes \"...\"      extra context / known rules",
            "  --notes-file <f>   same, read from a file",
            "  --project <n>      GitHub project number in testcases.json (default: 24)",
            "  --provider <name>  anthropic | openai | gemini | deepseek | xai | openrouter",
            "                     (default: guessed from the key; openai vs deepseek can't",
            "                     be told apart from the key alone - pass this if it guesses wrong)",
            "  --model <id>       model id                      (default: provider's flagship model)",
            "  --max-tokens <n>   response budget               (default: 16000)",
            "  --key <k>          API key, any of the providers above",
            "                     (else env ANTHROPIC_API_KEY / OPENAI_API_KEY / GEMINI_API_KEY /",
            "                     DEEPSEEK_API_KEY / XAI_API_KEY / OPENROUTER_API_KEY)",
            "  --dry-run          build the request, print a summary, no API call",
            "  --debug            write raw-response.json",
            "",
            "Outputs (in --out): testcases.json (n8n board contract), testcases.md, testcases.csv"
        ));
    }
}
