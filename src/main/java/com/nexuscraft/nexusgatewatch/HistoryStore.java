package com.nexuscraft.nexusgatewatch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Plain tab-separated history file, appended one line per event. */
public final class HistoryStore {

    private final File file;
    private final Logger logger;

    public HistoryStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Oldest-first list of the last {@code keep} readable entries. Trims the file if it has grown far past that. */
    public List<Entry> load(int keep) {
        List<Entry> out = new ArrayList<>();
        if (!file.exists()) {
            return out;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                Entry e = Entry.fromLine(line);
                if (e != null) {
                    out.add(e);
                }
            }
            if (out.size() > keep * 4) {
                out = new ArrayList<>(out.subList(out.size() - keep, out.size()));
                rewrite(out);
            }
        } catch (IOException e) {
            logger.warning("nexusgatewatch: could not read " + file.getName() + ": " + e.getMessage());
        }
        return out;
    }

    private void rewrite(List<Entry> entries) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Entry e : entries) {
            lines.add(e.toLine());
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    public synchronized void append(Entry entry) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(entry.toLine());
                w.newLine();
            }
        } catch (IOException e) {
            logger.warning("nexusgatewatch: could not write " + file.getName() + ": " + e.getMessage());
        }
    }
}
