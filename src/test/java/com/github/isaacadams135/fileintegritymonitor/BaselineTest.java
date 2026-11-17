package com.github.isaacadams135.fileintegritymonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineTest {

    private static final String BASELINE_NAME = ".fim-baseline";

    @TempDir
    Path tmp;

    @Test
    void initThenCheckIsClean() throws IOException {
        write("a.txt", "one");
        write("sub/b.txt", "two");

        Baseline saved = init(List.of());

        assertTrue(saved.compare(scan(List.of())).isEmpty());
        // the baseline file itself never counts as a file in the tree
        assertFalse(saved.entries().containsKey(BASELINE_NAME));
    }

    @Test
    void modifiedFileIsDetected() throws IOException {
        write("a.txt", "one");
        Baseline saved = init(List.of());

        write("a.txt", "changed");

        List<Change> changes = saved.compare(scan(List.of()));
        assertEquals(1, changes.size());
        assertEquals(Change.Kind.MODIFIED, changes.get(0).kind());
        assertEquals("a.txt", changes.get(0).path());
    }

    @Test
    void newFileIsAdded() throws IOException {
        write("a.txt", "one");
        Baseline saved = init(List.of());

        write("sub/new.txt", "fresh");

        List<Change> changes = saved.compare(scan(List.of()));
        assertEquals(1, changes.size());
        assertEquals(Change.Kind.ADDED, changes.get(0).kind());
        assertEquals("sub/new.txt", changes.get(0).path());
    }

    @Test
    void deletedFileIsRemoved() throws IOException {
        write("a.txt", "one");
        write("gone.txt", "bye");
        Baseline saved = init(List.of());

        Files.delete(tmp.resolve("gone.txt"));

        List<Change> changes = saved.compare(scan(List.of()));
        assertEquals(1, changes.size());
        assertEquals(Change.Kind.REMOVED, changes.get(0).kind());
        assertEquals("gone.txt", changes.get(0).path());
    }

    @Test
    void excludedPathsAreIgnored() throws IOException {
        write("keep.txt", "keep");
        write("noise.log", "log line");
        write("logs/deep.log", "nested log");
        write(".git/config", "git stuff");
        List<String> excludes = List.of("*.log", ".git");
        Baseline saved = init(excludes);

        write("noise.log", "changed");
        Files.delete(tmp.resolve("logs/deep.log"));
        write(".git/config", "changed too");

        assertEquals(1, saved.size());
        assertTrue(saved.compare(scan(excludes)).isEmpty());
    }

    @Test
    void missingBaselineFailsClearly() {
        IOException e = assertThrows(IOException.class,
                () -> Baseline.load(tmp.resolve(BASELINE_NAME)));

        assertTrue(e.getMessage().contains("baseline not found"));
        assertTrue(e.getMessage().contains("init"));
    }

    @Test
    void baselineRoundTripKeepsEntries() throws IOException {
        write("a.txt", "one");
        write("sub/b.txt", "two");
        Baseline saved = init(List.of());

        Baseline loaded = Baseline.load(tmp.resolve(BASELINE_NAME));

        assertEquals(saved.entries(), loaded.entries());
        assertTrue(saved.compare(loaded).isEmpty());

        List<String> lines = Files.readAllLines(tmp.resolve(BASELINE_NAME));
        assertTrue(lines.get(0).startsWith("# fim-baseline v1 "));
        assertTrue(lines.get(1).endsWith("a.txt")); // sorted by path
        assertTrue(lines.get(2).endsWith("sub/b.txt"));
        assertTrue(lines.get(1).contains("\t"));
    }

    private Baseline init(List<String> excludes) throws IOException {
        Path file = tmp.resolve(BASELINE_NAME);
        Baseline baseline = Baseline.scan(tmp, file, excludes);
        baseline.save(file);
        return baseline;
    }

    private Baseline scan(List<String> excludes) throws IOException {
        return Baseline.scan(tmp, tmp.resolve(BASELINE_NAME), excludes);
    }

    private Path write(String rel, String content) throws IOException {
        Path file = tmp.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
