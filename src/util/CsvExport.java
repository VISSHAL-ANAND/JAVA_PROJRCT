package util;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Minimal RFC-4180-style CSV row writer with spreadsheet formula-injection protection. */
public final class CsvExport {
    private CsvExport() {}

    public static String row(Object... values) {
        return Arrays.stream(values)
                .map(CsvExport::cell)
                .collect(Collectors.joining(","))
                + "\r\n";
    }

    private static String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String firstMeaningful = text.stripLeading();
        if (!firstMeaningful.isEmpty()
                && "=+-@".indexOf(firstMeaningful.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
