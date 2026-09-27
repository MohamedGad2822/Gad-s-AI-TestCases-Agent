package com.ignteq.tcagent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Turns a folder of screenshots / PDFs / notes into provider-neutral evidence blocks. */
public class Inputs {

    public final List<Block> blocks = new ArrayList<>();
    public int images = 0, pdfs = 0, texts = 0, skipped = 0;
    public long approxBytes = 0;

    private static final Set<String> IMG = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> TXT = Set.of("md", "txt", "csv", "json", "html", "htm", "xml",
                                                  "feature", "yml", "yaml", "log");

    public static Inputs scan(Path dir) throws IOException {
        if (!Files.isDirectory(dir))
            throw new IOException("input folder not found: " + dir.toAbsolutePath());
        return scan(List.of(dir));
    }

    /**
     * Accepts whatever was dropped onto the GUI (or passed on the CLI): a mix of
     * individual files and/or folders. Each folder is walked and its files are
     * labelled relative to that folder (matching the plain --in <dir> behaviour);
     * loose files are labelled by their own file name.
     */
    public static Inputs scan(List<Path> roots) throws IOException {
        Inputs in = new Inputs();
        List<Map.Entry<Path, String>> files = new ArrayList<>();

        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                try (Stream<Path> s = Files.walk(root)) {
                    for (Path p : s.filter(Files::isRegularFile)
                                   .filter(p -> !p.getFileName().toString().startsWith("."))
                                   .toList()) {
                        files.add(Map.entry(p, root.relativize(p).toString().replace('\\', '/')));
                    }
                }
            } else if (Files.isRegularFile(root)) {
                if (!root.getFileName().toString().startsWith("."))
                    files.add(Map.entry(root, root.getFileName().toString()));
            } else {
                System.err.println("  ! not found, skipped: " + root);
            }
        }

        files.sort(Comparator.comparing(e -> e.getValue().toLowerCase()));
        for (Map.Entry<Path, String> e : files) process(in, e.getKey(), e.getValue());

        if (in.blocks.isEmpty())
            throw new IOException("no usable files (want .png/.jpg/.webp/.gif/.pdf/.md/.txt/.csv/.json)");
        return in;
    }

    private static void process(Inputs in, Path p, String name) throws IOException {
        String ext = ext(name);
        byte[] data = Files.readAllBytes(p);

        if (IMG.contains(ext)) {
            in.label("SCREENSHOT: " + name);
            in.blocks.add(Block.image(mediaType(ext), data));
            in.images++;
            in.approxBytes += data.length;
        } else if ("pdf".equals(ext)) {
            in.label("DOCUMENT: " + name);
            in.blocks.add(Block.pdf(data));
            in.pdfs++;
            in.approxBytes += data.length;
        } else if (TXT.contains(ext)) {
            String body = new String(data, StandardCharsets.UTF_8);
            in.label("FILE: " + name + "\n-----\n" + body + "\n-----");
            in.texts++;
            in.approxBytes += data.length;
        } else {
            System.err.println("  ! skipped (unsupported type): " + name);
            in.skipped++;
        }
    }

    private void label(String text) {
        blocks.add(Block.text(text));
    }

    private static String mediaType(String ext) {
        switch (ext) {
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            default: return "image/jpeg";
        }
    }

    private static String ext(String name) {
        int d = name.lastIndexOf('.');
        return d < 0 ? "" : name.substring(d + 1).toLowerCase();
    }

    public String summary() {
        return images + " screenshot(s), " + pdfs + " pdf(s), " + texts + " text file(s)"
                + (skipped > 0 ? ", " + skipped + " skipped" : "")
                + String.format(" (~%.1f MB raw)", approxBytes / 1048576.0);
    }
}
