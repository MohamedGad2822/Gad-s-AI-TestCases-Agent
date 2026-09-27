package com.ignteq.tcagent;

import java.util.*;

/**
 * Tiny zero-dependency JSON parser/writer.
 * Values map to: Map<String,Object>, List<Object>, String, Double, Boolean, null.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
        this.i = 0;
    }

    // ---------- public API ----------

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        return v;
    }

    public static Map<String, Object> obj() {
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : Collections.emptyList();
    }

    public static String str(Object node, String key) {
        Object v = asMap(node).get(key);
        if (v == null) return "";
        if (v instanceof String) return (String) v;
        return String.valueOf(v);
    }

    public static List<String> strList(Object node, String key) {
        List<String> out = new ArrayList<>();
        for (Object o : asList(asMap(node).get(key))) {
            if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }

    public static boolean bool(Object node, String key) {
        Object v = asMap(node).get(key);
        return v instanceof Boolean ? (Boolean) v : "true".equalsIgnoreCase(String.valueOf(v));
    }

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, v, -1, 0);
        return sb.toString();
    }

    public static String pretty(Object v) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, v, 2, 0);
        return sb.toString();
    }

    /** Strips markdown fences and any prose around the JSON object a model returned. */
    public static String extractJson(String raw) {
        String t = raw.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            int fence = t.lastIndexOf("```");
            if (fence > 0) t = t.substring(0, fence);
            t = t.trim();
        }
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) t = t.substring(first, last + 1);
        return t;
    }

    // ---------- writer ----------

    private static void writeTo(StringBuilder sb, Object v, int indent, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            escape(sb, (String) v);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) sb.append((long) d);
            else sb.append(d);
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, indent, depth + 1);
                escape(sb, String.valueOf(e.getKey()));
                sb.append(':');
                if (indent >= 0) sb.append(' ');
                writeTo(sb, e.getValue(), indent, depth + 1);
            }
            nl(sb, indent, depth);
            sb.append('}');
        } else if (v instanceof Collection) {
            Collection<?> c = (Collection<?>) v;
            if (c.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            boolean first = true;
            for (Object o : c) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, indent, depth + 1);
                writeTo(sb, o, indent, depth + 1);
            }
            nl(sb, indent, depth);
            sb.append(']');
        } else {
            escape(sb, String.valueOf(v));
        }
    }

    private static void nl(StringBuilder sb, int indent, int depth) {
        if (indent < 0) return;
        sb.append('\n');
        for (int k = 0; k < indent * depth; k++) sb.append(' ');
    }

    private static void escape(StringBuilder sb, String v) {
        sb.append('"');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------- parser ----------

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        if (i >= s.length()) throw err("unexpected end of input");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            if (s.charAt(i) != ':') throw err("expected ':'");
            i++;
            ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == '}') { i++; return m; }
            throw err("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; // [
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == ']') { i++; return l; }
            throw err("expected ',' or ']'");
        }
    }

    private String string() {
        if (s.charAt(i) != '"') throw err("expected string");
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw err("bad escape \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(start, i);
        if (n.isEmpty()) throw err("invalid value");
        return Double.parseDouble(n);
    }

    private void expect(String lit) {
        if (!s.startsWith(lit, i)) throw err("expected " + lit);
        i += lit.length();
    }

    private RuntimeException err(String msg) {
        int from = Math.max(0, i - 40);
        int to = Math.min(s.length(), i + 40);
        return new IllegalArgumentException("JSON parse error at " + i + ": " + msg
                + "\n...' " + s.substring(from, to).replace("\n", " ") + " '...");
    }
}
