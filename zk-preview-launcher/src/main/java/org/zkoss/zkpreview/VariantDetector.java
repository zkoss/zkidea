package org.zkoss.zkpreview;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Detects whether a resolved ZK classpath targets the jakarta.servlet or
 * javax.servlet API by scanning {@code DHtmlLayoutServlet.class}'s own bytecode
 * for which servlet package it references -- no reflection/loading required.
 */
public final class VariantDetector {

    private static final String MARKER_CLASS = "org/zkoss/zk/ui/http/DHtmlLayoutServlet.class";
    private static final byte[] JAKARTA_MARKER = "jakarta/servlet".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JAVAX_MARKER = "javax/servlet".getBytes(StandardCharsets.US_ASCII);

    // The ZK core artifact is always named "zk-<version>.jar" (e.g. "zk-10.1.0-jakarta.jar",
    // "zk-9.6.0.2.jar") -- distinct from sibling artifacts like "zkbind-"/"zkmax-"/"zkex-"
    // which have no hyphen directly after "zk". Scanning this candidate first makes
    // detection deterministic and fast even when the caller hands us a much larger
    // classpath: once the plugin widened its handoff classpath to every
    // runtime library jar, not just ZK-named ones, an unrelated jar that incidentally
    // bundles a class at the same path as DHtmlLayoutServlet -- e.g. a shaded/uber-jar,
    // or a stale duplicate ZK jar -- could otherwise win a first-match-wins scan purely
    // by virtue of its position in the caller-supplied list).
    private static final Pattern ZK_CORE_JAR_NAME = Pattern.compile("zk-[^/\\\\]+\\.jar", Pattern.CASE_INSENSITIVE);

    private VariantDetector() {
    }

    public static ZkVariant detect(List<File> classpathEntries) throws IOException {
        byte[] classBytes = readMarkerClass(preferCanonicalZkCoreJar(classpathEntries));
        if (classBytes == null) {
            throw new IOException("Could not locate " + MARKER_CLASS + " on the supplied ZK classpath: "
                    + classpathEntries);
        }
        if (contains(classBytes, JAKARTA_MARKER)) {
            return ZkVariant.JAKARTA;
        }
        if (contains(classBytes, JAVAX_MARKER)) {
            return ZkVariant.JAVAX;
        }
        throw new IOException("DHtmlLayoutServlet.class references neither jakarta.servlet nor javax.servlet; "
                + "unrecognised ZK build");
    }

    /** Reorders {@code classpathEntries} so canonically-named ZK core jars are tried first. */
    private static List<File> preferCanonicalZkCoreJar(List<File> classpathEntries) {
        List<File> ordered = new ArrayList<>(classpathEntries.size());
        List<File> rest = new ArrayList<>();
        for (File entry : classpathEntries) {
            if (ZK_CORE_JAR_NAME.matcher(entry.getName()).matches()) {
                ordered.add(entry);
            } else {
                rest.add(entry);
            }
        }
        ordered.addAll(rest);
        return ordered;
    }

    private static byte[] readMarkerClass(List<File> classpathEntries) throws IOException {
        for (File entry : classpathEntries) {
            if (entry.isDirectory()) {
                File f = new File(entry, MARKER_CLASS);
                if (f.isFile()) {
                    return Files.readAllBytes(f.toPath());
                }
            } else if (entry.isFile()) {
                try (JarFile jar = new JarFile(entry)) {
                    JarEntry je = jar.getJarEntry(MARKER_CLASS);
                    if (je != null) {
                        try (InputStream is = jar.getInputStream(je)) {
                            return is.readAllBytes();
                        }
                    }
                } catch (IOException ignored) {
                    // Not a jar / unreadable entry -- keep scanning.
                }
            }
        }
        return null;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
