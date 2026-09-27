package com.ignteq.tcagent;

/** One piece of evidence (a label/note, a screenshot, or a PDF), independent of any API's wire format. */
public class Block {

    public enum Kind { TEXT, IMAGE, PDF }

    public final Kind kind;
    public final String text;       // TEXT
    public final String mediaType;  // IMAGE (e.g. image/png)
    public final byte[] data;       // IMAGE / PDF, raw bytes

    private Block(Kind kind, String text, String mediaType, byte[] data) {
        this.kind = kind;
        this.text = text;
        this.mediaType = mediaType;
        this.data = data;
    }

    public static Block text(String text) {
        return new Block(Kind.TEXT, text, null, null);
    }

    public static Block image(String mediaType, byte[] data) {
        return new Block(Kind.IMAGE, null, mediaType, data);
    }

    public static Block pdf(byte[] data) {
        return new Block(Kind.PDF, null, "application/pdf", data);
    }
}
