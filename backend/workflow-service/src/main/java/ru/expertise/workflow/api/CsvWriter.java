package ru.expertise.workflow.api;

import java.util.List;

/** Minimal RFC 4180 CSV rendering for the list exports (TZ §9 "выгрузка ... предусмотрена ролью"). */
final class CsvWriter {

    private CsvWriter() {
    }

    static String render(List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, header);
        rows.forEach(row -> appendRow(csv, row));
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(values.get(i)));
        }
        csv.append("\r\n");
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
