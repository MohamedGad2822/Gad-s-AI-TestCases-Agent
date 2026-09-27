package com.ignteq.tcagent;

import java.nio.file.Files;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && "--gui".equals(args[0]))) {
            javax.swing.SwingUtilities.invokeLater(Gui::launch);
            return;
        }
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println("run with --help for usage");
            System.exit(2);
        } catch (Exception e) {
            System.err.println("failed: " + e.getMessage());
            System.exit(3);
        }
    }

    static void run(String[] args) throws Exception {
        Config c = Config.parse(args);

        System.out.println("> reading " + c.in.toAbsolutePath());
        Inputs in = Inputs.scan(c.in);
        System.out.println("  " + in.summary());

        execute(c, in);
    }

    /**
     * Shared by the CLI and the GUI once Config + Inputs are ready. Returns the ordered
     * cases (empty on a dry run) so the GUI can render them, not just log a summary line.
     */
    static List<TestCase> execute(Config c, Inputs in) throws Exception {
        String task = Prompt.task(c, in);
        ApiClient api = Providers.forConfig(c);
        Map<String, Object> body = api.body(c, in.blocks, task);

        if (c.dryRun) {
            Files.createDirectories(c.out);
            Out.write(c.out.resolve("request-preview.json"), redact(body));
            System.out.println("> dry run: provider=" + c.provider.label() + ", model=" + c.model
                    + ", blocks=" + in.blocks.size() + ", module=" + c.module + ", ids=" + c.code + "-"
                    + String.format("%03d", c.start) + "+");
            System.out.println("  wrote " + c.out.resolve("request-preview.json"));
            return List.of();
        }

        System.out.println("> asking " + c.provider.label() + " " + c.model
                + " (this takes a minute for big batches)");
        Object response = api.send(c, body);

        String raw = api.text(response);
        if (c.debug) {
            Files.createDirectories(c.out);
            Out.write(c.out.resolve("raw-response.json"), raw);
        }
        if (api.truncated(response)) {
            System.err.println("  ! response hit the output token limit - suite is truncated."
                    + " Re-run with a higher --max-tokens or a smaller --count.");
        }

        Object parsed;
        try {
            parsed = Json.parse(Json.extractJson(raw));
        } catch (RuntimeException e) {
            Files.createDirectories(c.out);
            Out.write(c.out.resolve("raw-response.json"), raw);
            throw new IllegalStateException("model did not return valid JSON; raw text saved to "
                    + c.out.resolve("raw-response.json"));
        }

        List<TestCase> cases = new ArrayList<>();
        for (Object node : Json.asList(Json.asMap(parsed).get("testCases"))) {
            TestCase t = TestCase.from(node);
            if (!t.titleText.isEmpty()) cases.add(t);
        }
        if (cases.isEmpty()) throw new IllegalStateException("no test cases in the response");

        cases = TestCase.order(cases, c);
        Out.writeAll(c, cases, Json.str(parsed, "coverageNotes"));

        long p1 = cases.stream().filter(t -> t.priority.equals("P1")).count();
        long p2 = cases.stream().filter(t -> t.priority.equals("P2")).count();
        long p3 = cases.stream().filter(t -> t.priority.equals("P3")).count();
        long tbd = cases.stream().filter(t -> t.tbd).count();

        System.out.println("> " + cases.size() + " cases  (P1:" + p1 + " P2:" + p2 + " P3:" + p3
                + (tbd > 0 ? "  TBD:" + tbd : "") + ")  " + api.usage(response));
        System.out.println("  " + c.out.resolve("testcases.json") + "   <- drop this in the n8n folder");
        System.out.println("  " + c.out.resolve("testcases.md"));
        System.out.println("  " + c.out.resolve("testcases.csv"));
        return cases;
    }

    /**
     * Dry-run preview without megabytes of base64. Provider-agnostic: prompts and task
     * text stay fully readable; only base64 blobs (and OpenAI-style "data:...;base64,"
     * URLs wrapping them) get truncated, wherever they sit in the request tree. Both are
     * identified the same way regardless of provider: long AND whitespace-free, which
     * prose never is.
     */
    private static String redact(Map<String, Object> body) {
        return Json.pretty(redactValue(body)) + "\n";
    }

    private static Object redactValue(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) copy.put(String.valueOf(e.getKey()), redactValue(e.getValue()));
            return copy;
        }
        if (v instanceof List<?> l) {
            List<Object> copy = new ArrayList<>();
            for (Object o : l) copy.add(redactValue(o));
            return copy;
        }
        if (v instanceof String s && s.length() > 300 && s.chars().noneMatch(Character::isWhitespace)) {
            return "<data " + s.length() + " chars>";
        }
        return v;
    }
}
