package com.ignteq.tcagent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Writes the three deliverables. */
public class Out {

    public static void writeAll(Config c, List<TestCase> cases, String coverageNotes) throws IOException {
        Files.createDirectories(c.out);
        write(c.out.resolve("testcases.json"), boardJson(c, cases));
        write(c.out.resolve("testcases.md"), markdown(c, cases, coverageNotes));
        write(c.out.resolve("testcases.csv"), csv(cases));
    }

    /** Exact contract consumed by the n8n workflow -> GitHub Projects v2 board. */
    static String boardJson(Config c, List<TestCase> cases) {
        List<Object> arr = new ArrayList<>();
        for (TestCase t : cases) {
            Map<String, Object> m = Json.obj();
            m.put("id", t.id);
            m.put("title", t.fullTitle);
            m.put("module", c.module);
            m.put("type", t.type);
            m.put("priority", t.priority);
            m.put("preconditions", t.preconditions);
            m.put("steps", t.steps);
            m.put("expected", t.expected);
            arr.add(m);
        }
        Map<String, Object> root = Json.obj();
        root.put("projectNumber", c.projectNumber);
        root.put("testCases", arr);
        return Json.pretty(root) + "\n";
    }

    static String markdown(Config c, List<TestCase> cases, String coverageNotes) {
        Map<String, Integer> byPrio = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        int tbd = 0;
        for (TestCase t : cases) {
            byPrio.merge(t.priority, 1, Integer::sum);
            byType.merge(t.type, 1, Integer::sum);
            if (t.tbd) tbd++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(c.module).append(" - test suite\n\n");
        sb.append("Total: **").append(cases.size()).append("** cases");
        for (String p : List.of("P1", "P2", "P3")) {
            if (byPrio.containsKey(p)) sb.append(" | ").append(p).append(": ").append(byPrio.get(p));
        }
        for (Map.Entry<String, Integer> e : byType.entrySet()) {
            sb.append(" | ").append(e.getKey()).append(": ").append(e.getValue());
        }
        if (tbd > 0) sb.append(" | **TBD: ").append(tbd).append("**");
        sb.append("\n\n");

        if (coverageNotes != null && !coverageNotes.isBlank()) {
            sb.append("> **Coverage notes:** ").append(coverageNotes.trim()).append("\n\n");
        }

        String current = null;
        for (TestCase t : cases) {
            if (!t.section.equals(current)) {
                current = t.section;
                sb.append("\n## ").append(current).append("\n");
            }
            sb.append("\n### ").append(t.fullTitle).append("\n");
            sb.append("`").append(t.id).append("`");
            if (!t.evidence.isBlank()) sb.append("  -  evidence: ").append(t.evidence);
            sb.append("\n\n");
            if (!t.preconditions.isBlank())
                sb.append("**Preconditions:** ").append(t.preconditions).append("\n\n");
            sb.append("**Steps:**\n");
            for (int i = 0; i < t.steps.size(); i++) {
                sb.append(i + 1).append(". ").append(t.steps.get(i)).append("\n");
            }
            sb.append("\n**Expected result").append(t.tbd ? " - TBD" : "").append(":** ")
              .append(t.expected).append("\n");
        }
        return sb.toString();
    }

    static String csv(List<TestCase> cases) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("ID,Title,Section,Type,Priority,Preconditions,Steps,Expected Result,TBD,Evidence\n");
        for (TestCase t : cases) {
            List<String> numbered = new ArrayList<>();
            for (int i = 0; i < t.steps.size(); i++) numbered.add((i + 1) + ". " + t.steps.get(i));
            sb.append(q(t.id)).append(',')
              .append(q(t.fullTitle)).append(',')
              .append(q(t.section)).append(',')
              .append(q(t.type)).append(',')
              .append(q(t.priority)).append(',')
              .append(q(t.preconditions)).append(',')
              .append(q(String.join("\n", numbered))).append(',')
              .append(q(t.expected)).append(',')
              .append(t.tbd ? "YES" : "").append(',')
              .append(q(t.evidence)).append('\n');
        }
        return sb.toString();
    }

    private static String q(String v) {
        if (v == null) v = "";
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    static void write(Path p, String content) throws IOException {
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }
}
