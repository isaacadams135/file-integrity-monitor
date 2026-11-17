package com.github.isaacadams135.fileintegritymonitor;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every regular file under a directory, hashed with SHA-256 and keyed by its
 * path relative to the root. Saved as a greppable text file: one
 * "sha256hex &lt;tab&gt; size &lt;tab&gt; relative/path" line per file, sorted by path.
 */
public final class Baseline {

    private static final String HEADER = "# fim-baseline v1 ";

    private final String timestamp;
    private final Map<String, Entry> entries; // sorted by path

    public record Entry(String hash, long size) {
    }

    Baseline(String timestamp, Map<String, Entry> entries) {
        this.timestamp = timestamp;
        this.entries = entries;
    }

    /**
     * Hashes the tree, skipping excluded paths and the baseline file itself so
     * a baseline stored inside the watched directory never reports as "added".
     */
    public static Baseline scan(Path root, Path baselineFile, List<String> excludes) throws IOException {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String glob : excludes) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        Path skip = baselineFile.toAbsolutePath().normalize();
        Map<String, Entry> found = new TreeMap<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && isExcluded(root.relativize(dir), matchers)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || file.toAbsolutePath().normalize().equals(skip)) {
                    return FileVisitResult.CONTINUE;
                }
                if (isExcluded(root.relativize(file), matchers)) {
                    return FileVisitResult.CONTINUE;
                }
                found.put(relativize(root, file), new Entry(Sha256.hash(file), attrs.size()));
                return FileVisitResult.CONTINUE;
            }
        });

        return new Baseline(Instant.now().toString(), found);
    }

    public void save(Path file) throws IOException {
        StringBuilder out = new StringBuilder(HEADER).append(timestamp).append('\n');
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            out.append(entry.getValue().hash()).append('\t')
                    .append(entry.getValue().size()).append('\t')
                    .append(entry.getKey()).append('\n');
        }
        Files.writeString(file, out);
    }

    public static Baseline load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("baseline not found: " + file + " (run 'init' first)");
        }
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty() || !lines.get(0).startsWith(HEADER)) {
            throw new IOException("not a fim baseline file: " + file);
        }
        Map<String, Entry> loaded = new TreeMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length != 3) {
                throw new IOException("bad baseline line " + (i + 1) + ": " + line);
            }
            try {
                loaded.put(parts[2], new Entry(parts[0], Long.parseLong(parts[1])));
            } catch (NumberFormatException e) {
                throw new IOException("bad baseline line " + (i + 1) + ": " + line);
            }
        }
        return new Baseline(lines.get(0).substring(HEADER.length()), loaded);
    }

    /** Differences between this baseline and a freshly scanned tree. */
    public List<Change> compare(Baseline current) {
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            Entry now = current.entries.get(entry.getKey());
            if (now == null) {
                changes.add(new Change(Change.Kind.REMOVED, entry.getKey(), shortHash(entry.getValue().hash())));
            } else if (!now.hash().equals(entry.getValue().hash())) {
                changes.add(new Change(Change.Kind.MODIFIED, entry.getKey(),
                        shortHash(entry.getValue().hash()) + " -> " + shortHash(now.hash())));
            }
        }
        for (Map.Entry<String, Entry> entry : current.entries.entrySet()) {
            if (!entries.containsKey(entry.getKey())) {
                changes.add(new Change(Change.Kind.ADDED, entry.getKey(), shortHash(entry.getValue().hash())));
            }
        }
        return changes;
    }

    public int size() {
        return entries.size();
    }

    Map<String, Entry> entries() {
        return entries;
    }

    // A bare pattern like "*.log" or ".git" should bite at any depth, so match
    // it against each path segment too — glob "*" alone never crosses a separator.
    private static boolean isExcluded(Path rel, List<PathMatcher> matchers) {
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(rel)) {
                return true;
            }
            for (Path segment : rel) {
                if (matcher.matches(segment)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String shortHash(String hash) {
        return hash.substring(0, 8);
    }
}
