package com.github.isaacadams135.fileintegritymonitor;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

public final class Main {

    private static final String USAGE = """
        usage: file-integrity-monitor <command> [options] <dir>

        commands:
          init <dir>       write a baseline for <dir>
          check <dir>      compare <dir> against the baseline
          watch <dir>      re-check every interval until Ctrl+C

        options:
          -b <file>        baseline file (default: ./.fim-baseline)
          -e <glob>        exclude paths matching glob, relative to <dir>
                           (repeatable, e.g. -e "*.log" -e ".git")
          -i <seconds>     watch interval in seconds (default: 5)
          -h, --help       show this help

        exit codes: 0 clean, 1 changes or missing baseline, 2 usage error
        """;

    private Main() {
    }

    public static void main(String[] args) {
        Options opts;
        try {
            opts = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        if (opts.help()) {
            System.out.println(USAGE);
            return;
        }

        int code;
        try {
            code = switch (opts.command()) {
                case "init" -> init(opts);
                case "check" -> check(opts);
                case "watch" -> watch(opts);
                default -> 2; // parse() already rejected anything else
            };
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            code = 1;
        }
        if (code != 0) {
            System.exit(code);
        }
    }

    private static int init(Options opts) throws IOException {
        if (!Files.isDirectory(opts.dir())) {
            throw new IOException("not a directory: " + opts.dir());
        }
        Baseline baseline = Baseline.scan(opts.dir(), opts.baseline(), opts.excludes());
        baseline.save(opts.baseline());
        System.out.printf("baseline written: %d files%n", baseline.size());
        return 0;
    }

    private static int check(Options opts) throws IOException {
        if (!Files.isDirectory(opts.dir())) {
            throw new IOException("not a directory: " + opts.dir());
        }
        Baseline baseline = Baseline.load(opts.baseline());
        Baseline current = Baseline.scan(opts.dir(), opts.baseline(), opts.excludes());
        return report(baseline.compare(current));
    }

    private static int watch(Options opts) throws IOException {
        Baseline.load(opts.baseline()); // fail fast when there is no baseline yet
        System.out.printf("watching %s every %ds, press Ctrl+C to stop%n", opts.dir(), opts.interval());
        while (true) {
            System.out.printf("%ncheck %s%n", Instant.now());
            check(opts);
            try {
                Thread.sleep(Duration.ofSeconds(opts.interval()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
        }
    }

    private static int report(List<Change> changes) {
        if (changes.isEmpty()) {
            System.out.println("no changes");
            return 0;
        }
        List<Change> modified = new ArrayList<>();
        List<Change> added = new ArrayList<>();
        List<Change> removed = new ArrayList<>();
        for (Change change : changes) {
            switch (change.kind()) {
                case MODIFIED -> modified.add(change);
                case ADDED -> added.add(change);
                case REMOVED -> removed.add(change);
            }
        }
        printSection("modified:", modified);
        printSection("added:", added);
        printSection("removed:", removed);
        System.out.printf("%d modified, %d added, %d removed%n",
                modified.size(), added.size(), removed.size());
        return 1;
    }

    private static void printSection(String title, List<Change> section) {
        if (section.isEmpty()) {
            return;
        }
        System.out.println(title);
        for (Change change : section) {
            System.out.printf("  %s  %s%n", change.path(), change.detail());
        }
    }

    record Options(String command, Path dir, Path baseline, List<String> excludes,
                   int interval, boolean help) {

        static Options parse(String[] args) {
            String command = null;
            Path dir = null;
            Path baseline = Path.of(".fim-baseline");
            List<String> excludes = new ArrayList<>();
            int interval = 5;
            boolean help = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--help" -> help = true;
                    case "-b" -> baseline = Path.of(nextValue(args, ++i, arg));
                    case "-e" -> {
                        String glob = nextValue(args, ++i, arg);
                        try {
                            FileSystems.getDefault().getPathMatcher("glob:" + glob);
                        } catch (PatternSyntaxException e) {
                            throw new IllegalArgumentException("bad glob: " + glob);
                        }
                        excludes.add(glob);
                    }
                    case "-i" -> {
                        String value = nextValue(args, ++i, arg);
                        try {
                            interval = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("bad interval: " + value);
                        }
                    }
                    default -> {
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("unknown option: " + arg);
                        }
                        if (command == null) {
                            command = arg;
                        } else if (dir == null) {
                            dir = Path.of(arg);
                        } else {
                            throw new IllegalArgumentException("unexpected argument: " + arg);
                        }
                    }
                }
            }

            if (help) {
                return new Options(null, null, baseline, List.of(), 0, true);
            }
            if (command == null) {
                throw new IllegalArgumentException("missing command");
            }
            if (!command.equals("init") && !command.equals("check") && !command.equals("watch")) {
                throw new IllegalArgumentException("unknown command: " + command);
            }
            if (dir == null) {
                throw new IllegalArgumentException("missing directory");
            }
            if (interval < 1) {
                throw new IllegalArgumentException("interval must be at least 1 second");
            }
            return new Options(command, dir, baseline, List.copyOf(excludes), interval, false);
        }

        private static String nextValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return args[index];
        }
    }
}
