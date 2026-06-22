package com.ssm.util;

public final class CsvExportUtils {
    private CsvExportUtils() {
    }

    public static String cell(String value) {
        String text = value == null ? "" : value;
        if (looksLikeFormula(text)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean looksLikeFormula(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        if (index >= value.length()) {
            return false;
        }
        char first = value.charAt(index);
        return first == '=' || first == '+' || first == '-' || first == '@';
    }
}
