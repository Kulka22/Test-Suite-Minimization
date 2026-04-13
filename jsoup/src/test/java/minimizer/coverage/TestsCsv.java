package minimizer.coverage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestsCsv {
    private static final String HEADER = "unique_test_id,test_name,status,exec_file_path";

    private TestsCsv() {
    }

    public static void write(Path csvPath, List<TestRunRecord> records) throws IOException {
        Files.createDirectories(csvPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (TestRunRecord record : records) {
                writer.write(escape(record.uniqueTestId()));
                writer.write(',');
                writer.write(escape(record.testName()));
                writer.write(',');
                writer.write(record.status().name());
                writer.write(',');
                writer.write(escape(record.execFilePath()));
                writer.newLine();
            }
        }
    }

    public static List<TestRunRecord> read(Path csvPath) throws IOException {
        List<TestRunRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                return records;
            }
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 4) {
                    continue;
                }
                records.add(new TestRunRecord(cols.get(0), cols.get(1), TestStatus.valueOf(cols.get(2)), cols.get(3)));
            }
        }
        return records;
    }

    private static String escape(String value) {
        String safe = value == null ? "" : value;
        if (!safe.contains(",") && !safe.contains("\"") && !safe.contains("\n")) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static List<String> parseCsvLine(String line) {
        List<String> cols = new ArrayList<>(4);
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cols.add(cur.toString());
        return cols;
    }
}
