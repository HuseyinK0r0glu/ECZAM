package com.eczam.medications.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cleaning rules for the Tip-Atlası {@code ilac} source export, derived from a
 * direct inspection of the data (see {@code plans/medications-schema-plan.md}).
 *
 * <p>The dominant Description placeholder is a bare {@code "-"} (16,976 rows),
 * not the one documented sentence; real leaflets average ~15k chars. So
 * placeholder detection is data-driven, not a single hardcoded string.
 */
public final class SourceText {

    private SourceText() {}

    /** Matches "…bulunamadı." style placeholders (ASCII prefix → Turkish-safe). */
    private static final Pattern NOT_FOUND = Pattern.compile("bulunamad", Pattern.CASE_INSENSITIVE);

    /** Minimum real-leaflet length (observed real min was 166). */
    private static final int MIN_LEAFLET_LEN = 150;

    /** Source scrape ceiling (~32,767 chars); flag as truncated near it. */
    private static final int TRUNCATION_THRESHOLD = 32_700;

    /** A description is a real leaflet (not a placeholder) per the three-part rule. */
    public static boolean isRealLeaflet(String description) {
        if (description == null) return false;
        String d = description.trim();
        if (d.isEmpty() || d.equals("-")) return false;
        if (NOT_FOUND.matcher(d).find()) return false;
        return d.length() >= MIN_LEAFLET_LEN;
    }

    public static boolean isTruncated(String description) {
        return description != null && description.trim().length() >= TRUNCATION_THRESHOLD;
    }

    /** Returns the active ingredient, or null when the source value is a placeholder/empty. */
    public static String cleanActiveIngredient(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty() || v.equals("-") || NOT_FOUND.matcher(v).find()) return null;
        return v;
    }

    /**
     * Cleans the 5 source category columns into an ordered path: drops empty,
     * {@code -}, {@code 0}, {@code Yok} sentinels and a leaf identical to its
     * parent (the frequent Cat4=Cat5 duplication). Returns null when nothing
     * survives.
     */
    public static List<String> cleanCategoryPath(String... categories) {
        List<String> out = new ArrayList<>();
        for (String c : categories) {
            if (c == null) continue;
            String v = c.trim();
            if (v.isEmpty() || v.equals("-") || v.equals("0")
                    || v.equalsIgnoreCase("yok")) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).equalsIgnoreCase(v)) continue;
            out.add(v);
        }
        return out.isEmpty() ? null : out;
    }

    /** ATC anatomical main group = uppercase first letter, or null. */
    public static String atcGroup(String atcCode) {
        if (atcCode == null || atcCode.isBlank()) return null;
        char c = atcCode.trim().charAt(0);
        return Character.isLetter(c) ? String.valueOf(Character.toUpperCase(c)) : null;
    }

    public static String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }
}
