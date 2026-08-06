package org.zkoss.zkidea.preview;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Filters a module's runtime classpath down to the ZK framework jars, and computes a
 * signature over the resolved set so {@link ZulPreviewServerService} can tell whether an
 * already-running preview helper JVM can be reused or must be restarted.
 */
public final class ZkClasspathFilter {

    // Artifact-id prefixes that identify a ZK framework jar. Every released ZK jar follows
    // the Maven convention "<artifactId>-<version>[-<variant>].jar" (e.g. "zk-9.6.4.jar",
    // "zul-10.1.0-jakarta.jar"), so a name-prefix match is enough here.
    // Addon-only jars are included deliberately: a module may depend on an addon without a
    // core zk-*.jar of its own. zkcharts/zkpivot follow the same convention; Keikai's are
    // named "keikai-<version>.jar".
    // Known gap: this list gates only "does this module have any ZK at all", and an addon
    // whose artifact name falls outside it would be misjudged as "no ZK". The classpath
    // actually handed to the launcher is unaffected -- filterLibraryJars passes every
    // library jar regardless of name.
    private static final List<String> ZK_ARTIFACT_PREFIXES = List.of(
            "zk-", "zul-", "zkbind-", "zcommon-", "zweb-", "zel-", "zhtml-",
            "zkmax-", "zkex-", "zuti-", "zkplus-", "zkcharts-", "zkpivot-", "keikai-");

    private ZkClasspathFilter() {
    }

    public static boolean isZkJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String prefix : ZK_ARTIFACT_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a module's classpath actually carries usable ZK jars (U3). */
    public enum ZkPresence {
        /** No {@code zk-*}/{@code zul-*}/... named entry at all -- the module has no ZK dependency. */
        NONE,
        /** A ZK jar is declared but no such file exists on disk -- a wiped repo cache / dangling path. */
        DECLARED_BUT_MISSING,
        /** At least one declared ZK jar exists on disk -- good to hand to the launcher. */
        PRESENT
    }

    /**
     * Classifies a module's ZK dependency state (U3). {@link #filterZkJars} matches by <em>filename</em>
     * only (the presence gate), while {@link #filterLibraryJars} requires an existing file: a declared
     * ZK jar whose file was wiped (dangling local-repo path) is "declared" but not usable. Distinguishing
     * the two lets the UI say "re-import/re-sync" instead of the wrong "add a ZK dependency" for a
     * dependency the user already declared.
     */
    public static ZkPresence detectZkPresence(List<String> classpathEntries) {
        List<File> zkNamed = filterZkJars(classpathEntries);
        if (zkNamed.isEmpty()) {
            return ZkPresence.NONE;
        }
        for (File zk : zkNamed) {
            if (zk.isFile()) {
                return ZkPresence.PRESENT;
            }
        }
        return ZkPresence.DECLARED_BUT_MISSING;
    }

    public static List<File> filterZkJars(List<String> classpathEntries) {
        List<File> result = new ArrayList<>();
        for (String entry : classpathEntries) {
            File file = new File(entry);
            if (isZkJar(file.getName())) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Returns every classpath entry that is a library jar -- i.e. not a directory.
     * Module output directories (where the previewed module's own compiled classes,
     * including any ViewModel/Composer, live) are always excluded; everything else
     * (every resolved runtime dependency, ZK or not) is handed to the preview launcher
     * so it has the full runtime environment ZK actually needs to bootstrap (e.g.
     * ZK's {@code WebManager} requires {@code slf4j-api}, which is not a ZK-prefixed
     * jar). Isolation from user classes is guaranteed by the launcher's {@code UiFactory}
     * hook, not by classpath narrowness: this method's only isolation contract is
     * "never a directory".
     *
     * <p><b>Do not narrow this to ZK-named jars.</b> An earlier version did exactly that
     * and every preview died at ZK bootstrap with
     * {@code NoClassDefFoundError: org.slf4j.LoggerFactory} from {@code WebManager.<clinit>}
     * inside the scoped loader -- the defect that {@link #filterZkJars} now exists only to
     * gate on ("has this module any ZK?"), never to build a classpath from.
     *
     * <p>Also drops any entry that isn't an existing regular file: {@code OrderEnumerator}
     * can hand back project-SDK pseudo-entries such as
     * {@code .../zulu-24.jdk/Contents/Home!/java.base} (JDK module roots) -- observed on a
     * live launcher process before {@code .withoutSdk()} was added upstream -- which are
     * neither directories nor openable jars and would otherwise reach the launcher's
     * {@code --classpath} verbatim.
     */
    public static List<File> filterLibraryJars(List<String> classpathEntries) {
        List<File> result = new ArrayList<>();
        for (String entry : classpathEntries) {
            File file = new File(entry);
            if (file.isFile()) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Returns every entry that is an existing directory -- the module's resource roots
     * (e.g. {@code src/main/resources}), where a user's own {@code ~./} pages live
     * ({@code web/*.zul}, served by ZK's {@code ClassWebResource} from the classpath at
     * {@code /web/}). These ARE handed to the launcher, unlike the module <em>output</em>
     * directory (which {@link #filterLibraryJars} still excludes, AC-4(i)): a resource root
     * contains resources, not compiled user classes, so class-isolation -- guaranteed by the
     * launcher's {@code UiFactory} hook, not by classpath narrowness -- is unaffected, while
     * ZK's {@code ClassWebResource} can now resolve a user's {@code ~./} pages exactly as a
     * real servlet container does (where {@code WEB-INF/classes/web/} is on the classpath).
     *
     * <p>Mirror image of {@link #filterLibraryJars} (which keeps files and drops directories):
     * this keeps directories and drops files / non-existent paths.
     */
    public static List<File> filterResourceRoots(List<String> resourceRootPaths) {
        List<File> result = new ArrayList<>();
        for (String entry : resourceRootPaths) {
            File file = new File(entry);
            if (file.isDirectory()) {
                result.add(file);
            }
        }
        return result;
    }

    /** How many ZK jar names {@link #classpathSummary} lists before it starts counting. */
    static final int MAX_SUMMARY_JARS = 12;

    /**
     * One-line description of a module's resolved classpath for a preview failure's GitHub issue:
     * the ZK jar <em>file names</em> plus the total entry count, e.g.
     * {@code "zk-10.0.0.jar, zul-10.0.0.jar (+3 more) [24 classpath entries]"}.
     *
     * <p>This is the single most diagnostic fact about a failed render -- it carries the ZK
     * version, CE vs EE, and any missing transitive (the documented {@code zkex}/{@code
     * CometServerPush} failure is exactly a "wrong jar set" failure) -- and used to be absent from
     * every report (doc/zul_preview_spec.md §2.7).
     *
     * <p>Deliberate choices: <b>names only</b>, never absolute paths -- those are long and leak the
     * reporter's home directory into a public issue; <b>ZK jars only</b>, because the rest is noise
     * that would blow the issue-URL budget (the total count still says how much else was there);
     * and <b>classpath order preserved</b>, not sorted, so a stale duplicate shadowing a good jar
     * stays visible.
     *
     * <p>Takes the raw resolved entries rather than the filtered launcher classpath so a ZK jar
     * that is declared but missing on disk still shows up -- that is precisely the
     * {@link ZkPresence#DECLARED_BUT_MISSING} case a reporter needs to see.
     */
    public static String classpathSummary(List<String> classpathEntries) {
        List<File> zkJars = filterZkJars(classpathEntries);
        StringBuilder sb = new StringBuilder();
        if (zkJars.isEmpty()) {
            sb.append("none");
        } else {
            int listed = Math.min(zkJars.size(), MAX_SUMMARY_JARS);
            for (int i = 0; i < listed; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(zkJars.get(i).getName());
            }
            if (zkJars.size() > listed) {
                sb.append(" (+").append(zkJars.size() - listed).append(" more)");
            }
        }
        return sb.append(" [").append(classpathEntries.size()).append(" classpath entries]").toString();
    }

    /**
     * Stable signature over a jar set: it changes iff the set of paths, sizes, or
     * modification times changes -- i.e. iff the actually-resolved classpath changed.
     */
    public static String signature(List<File> zkJars) {
        List<File> sorted = new ArrayList<>(zkJars);
        sorted.sort(Comparator.comparing(File::getAbsolutePath));
        StringBuilder sb = new StringBuilder();
        for (File file : sorted) {
            sb.append(file.getAbsolutePath()).append('|')
                    .append(file.length()).append('|')
                    .append(file.lastModified()).append(';');
        }
        return sha256Hex(sb.toString());
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JDK -- unreachable in practice.
            throw new IllegalStateException(e);
        }
    }
}
