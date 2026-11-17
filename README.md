# file-integrity-monitor

A small CLI that baselines a directory of files with SHA-256 hashes and tells you what changed since. I built it while learning how tamper detection works — the same idea as `aide`/`tripwire` but small enough to read in one sitting. Runs as a normal user on JDK 21+ with no dependencies beyond the JDK.

## Features

- `init` hashes every regular file in a tree and writes a greppable baseline (`sha256hex <tab> size <tab> path`)
- `check` re-hashes the tree and reports modified, added and removed files with short hash previews
- `watch` re-checks on an interval until Ctrl+C, printing a timestamped check each pass
- Repeatable `-e` glob excludes (e.g. `-e "*.log" -e ".git"`) matched against the relative path and each path segment, so bare patterns bite at any depth
- The baseline file itself is skipped, so a baseline stored inside the watched directory never reports as "added"
- Scanning, loading and comparing are pure functions over the tree, so the whole thing is unit tested without touching the clock

## Build

Requires JDK 21+.

```bash
./gradlew build
./gradlew jar
```

On Windows use `gradlew.bat`.

## Usage

```bash
./gradlew run --args="init ./samples/demo"
./gradlew run --args="check ./samples/demo"
./gradlew run --args="watch ./samples/demo -i 10 -b .fim-baseline -e *.log"
./gradlew run --args="--help"
```

Exit codes: 0 clean, 1 changes found (or missing baseline), 2 usage error.

Real output after editing a file, adding one and deleting one in `samples/demo`:

```text
modified:
  config.ini    a1b2c3d4 -> 9f8e7d6c
added:
  rogue.sh      4c1d9a02
removed:
  backdoor.sh   7e3b5f11
1 modified, 1 added, 1 removed
```

## How it works

`init` walks the tree with `Files.walkFileTree`, hashes each regular file with `MessageDigest` (SHA-256), and saves a sorted text baseline with a `# fim-baseline v1 <timestamp>` header. `check` loads the baseline, rescans the tree the same way and diffs the two maps: a hash mismatch is MODIFIED, a key only in the old baseline is REMOVED, a key only in the new scan is ADDED. `watch` just loops `check` on a sleep interval. Nothing is written during a check, so the tool never disturbs what it is measuring.

## Only use it on stuff you're allowed to

Authorized targets only.

## License

MIT
