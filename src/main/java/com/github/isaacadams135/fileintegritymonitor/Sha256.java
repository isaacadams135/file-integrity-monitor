package com.github.isaacadams135.fileintegritymonitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of a file's contents, as lowercase hex. */
public final class Sha256 {

    private Sha256() {
    }

    public static String hash(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM ships SHA-256", e);
        }
        byte[] buf = new byte[8192];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
