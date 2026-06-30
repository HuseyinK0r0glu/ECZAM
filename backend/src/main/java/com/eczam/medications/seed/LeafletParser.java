package com.eczam.medications.seed;

import com.eczam.medications.LeafletSections;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a Turkish patient leaflet (<i>Kullanma Talimatı</i>) into its standard
 * numbered sections. 99% of real leaflets in the source follow this structure,
 * so section boundaries (not fixed-size windows) are the primary chunk unit —
 * this lets the RAG assistant cite which leaflet section an answer came from
 * (CLAUDE.md §7 guardrail).
 */
public final class LeafletParser {

    private LeafletParser() {}

    /** Canonical KT sections, in order, each with its heading anchor pattern. */
    public enum Section {
        WHAT_IS(1, "what_is", "NED[İI]R\\s+VE\\s+NE\\s+[İI]Ç[İI]N\\s+KULLANIL"),
        BEFORE_USE(2, "before_use", "ÖNCE\\s+D[İI]KKAT\\s+ED[İI]LMES[İI]"),
        HOW_TO_USE(3, "how_to_use", "NASIL\\s+KULLANIL"),
        SIDE_EFFECTS(4, "side_effects", "YAN\\s+ETK[İI]LER"),
        STORAGE(5, "storage", "SAKLANMASI");

        public final int ordinal;
        public final String key;
        final Pattern pattern;

        Section(int ordinal, String key, String regex) {
            this.ordinal = ordinal;
            this.key = key;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
    }

    /** One parsed section with its character offsets into the original raw text. */
    public record Block(int ordinal, String key, String text, int charStart, int charLen) {}

    /**
     * Returns the leaflet's sections in document order. When fewer than two
     * anchors are found (unstructured leaflet, ~1% of rows) returns an empty
     * list and the caller should fall back to fixed-size chunking.
     */
    public static List<Block> parse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        record Anchor(int index, Section section) {}
        List<Anchor> anchors = new ArrayList<>();
        for (Section s : Section.values()) {
            Matcher m = s.pattern.matcher(raw);
            if (m.find()) anchors.add(new Anchor(m.start(), s));
        }
        if (anchors.size() < 2) return List.of();
        anchors.sort((a, b) -> Integer.compare(a.index(), b.index()));

        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            int start = anchors.get(i).index();
            int end = (i + 1 < anchors.size()) ? anchors.get(i + 1).index() : raw.length();
            String text = raw.substring(start, end).trim();
            if (text.isEmpty()) continue;
            Section s = anchors.get(i).section();
            blocks.add(new Block(s.ordinal, s.key, text, start, end - start));
        }
        return blocks;
    }

    /**
     * Best-effort mapping of the parsed sections onto the brief's
     * {@link LeafletSections} record (used for fast, non-RAG display). Lossy by
     * design — {@code leaflet_raw} retains the complete text and RAG chunks are
     * built from the parsed blocks, not from this projection.
     */
    public static LeafletSections toLeafletSections(List<Block> blocks) {
        String howToUse = null, sideEffects = null, beforeUse = null, storage = null;
        for (Block b : blocks) {
            switch (b.key()) {
                case "how_to_use" -> howToUse = b.text();
                case "side_effects" -> sideEffects = b.text();
                case "before_use" -> beforeUse = b.text();
                case "storage" -> storage = b.text();
                default -> { /* what_is has no target field */ }
            }
        }
        return new LeafletSections(howToUse, sideEffects, beforeUse, storage, null, null);
    }
}
