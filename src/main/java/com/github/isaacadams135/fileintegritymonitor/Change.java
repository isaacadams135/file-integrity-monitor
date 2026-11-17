package com.github.isaacadams135.fileintegritymonitor;

/** One difference between the baseline and the tree on disk. */
public record Change(Kind kind, String path, String detail) {

    public enum Kind { MODIFIED, ADDED, REMOVED }
}
