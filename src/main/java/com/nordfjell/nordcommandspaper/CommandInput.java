package com.nordfjell.nordcommandspaper;

import java.util.Locale;
import java.util.regex.Pattern;

/** Parses only the routing root, never rewrites the command or its arguments. */
final class CommandInput {
    static final int MAX_INPUT = 32767;
    private static final Pattern ROOT = Pattern.compile("[A-Za-z0-9_-]{1,64}(?::[A-Za-z0-9_-]{1,64})?");
    static String executionLabel(String input) {
        if (input == null || !input.startsWith("/")) return null;
        return label(input);
    }
    static String label(String input) {
        if (input == null || input.isEmpty() || input.length() > MAX_INPUT) return null;
        if (input.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029))
            return null;
        int start = input.charAt(0) == '/' ? 1 : 0;
        int end = input.indexOf(' ', start);
        if (end < 0) end = input.length();
        if (end == start || end - start > 129) return null;
        String root = input.substring(start, end);
        if (!ROOT.matcher(root).matches()) return null;
        return root.toLowerCase(Locale.ROOT);
    }
    static boolean administrative(String root) {
        return "nordcommands".equals(root) || "nordcommands:nordcommands".equals(root);
    }
}

