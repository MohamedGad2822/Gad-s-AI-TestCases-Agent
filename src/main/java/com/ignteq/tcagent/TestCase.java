package com.ignteq.tcagent;

import java.util.*;

/** One test case, numbered and titled by the tool (not by the model). */
public class TestCase {

    public String section = "General";
    public String titleText = "";
    public String type = "Positive";
    public String priority = "P2";
    public String preconditions = "";
    public List<String> steps = new ArrayList<>();
    public String expected = "";
    public boolean tbd = false;
    public String evidence = "";

    public int number;        // per-module sequence
    public String id;         // e.g. FF-001
    public String fullTitle;  // Module | N- Title (Type)(Priority)

    private static final List<String> TYPES = List.of("Positive", "Negative", "Edge case", "Validation");
    private static final List<String> PRIOS = List.of("P1", "P2", "P3");

    public static TestCase from(Object node) {
        TestCase t = new TestCase();
        t.section = blankTo(Json.str(node, "section"), "General").trim();
        t.titleText = Json.str(node, "titleText").trim();
        if (t.titleText.isEmpty()) t.titleText = Json.str(node, "title").trim();
        t.type = normalise(Json.str(node, "type"), TYPES, "Positive");
        t.priority = normalise(Json.str(node, "priority"), PRIOS, "P2");
        t.preconditions = Json.str(node, "preconditions").trim();
        t.steps = Json.strList(node, "steps");
        t.expected = Json.str(node, "expected").trim();
        t.tbd = Json.bool(node, "tbd") || t.expected.toUpperCase().startsWith("TBD");
        t.evidence = Json.str(node, "evidence").trim();
        return t;
    }

    /** Groups by section (first-seen order), sorts P1-P2-P3 inside, numbers continuously. */
    public static List<TestCase> order(List<TestCase> cases, Config c) {
        Map<String, List<TestCase>> bySection = new LinkedHashMap<>();
        for (TestCase t : cases) bySection.computeIfAbsent(t.section, k -> new ArrayList<>()).add(t);

        List<TestCase> ordered = new ArrayList<>();
        for (List<TestCase> group : bySection.values()) {
            group.sort(Comparator.comparingInt(x -> PRIOS.indexOf(x.priority)));
            ordered.addAll(group);
        }

        int n = c.start;
        for (TestCase t : ordered) {
            t.number = n++;
            t.id = String.format("%s-%03d", c.code, t.number);
            t.fullTitle = c.module + " | " + t.number + "- " + t.titleText
                    + " (" + t.type + ")(" + t.priority + ")";
        }
        return ordered;
    }

    public String stepsBlock() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            sb.append("   ").append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static String normalise(String v, List<String> allowed, String dflt) {
        if (v == null) return dflt;
        String s = v.trim();
        for (String a : allowed) if (a.equalsIgnoreCase(s)) return a;
        // tolerate common variants from the model
        String low = s.toLowerCase();
        if (low.startsWith("edge")) return "Edge case";
        if (low.startsWith("neg")) return "Negative";
        if (low.startsWith("val")) return "Validation";
        if (low.startsWith("pos") || low.startsWith("func")) return "Positive";
        if (low.contains("1") || low.startsWith("crit") || low.startsWith("high")) {
            if (allowed == PRIOS) return low.contains("1") || low.startsWith("crit") ? "P1" : "P2";
        }
        if (low.contains("2")) return "P2";
        if (low.contains("3") || low.startsWith("med") || low.startsWith("low")) return "P3";
        return dflt;
    }

    private static String blankTo(String v, String dflt) {
        return (v == null || v.isBlank()) ? dflt : v;
    }
}
