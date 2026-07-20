package org.zkoss.zkpreview;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a render-time exception into a structured {@link RenderError} (AC-6).
 * Deliberately works only against {@link Throwable}'s standard API (never casts to
 * a concrete ZK exception type) so it needs no ZK compile dependency (AC-2) --
 * the launcher's main sourceSet never imports ZK classes.
 */
public final class ErrorMapper {

    private static final int MAX_CHAIN_DEPTH = 25;
    private static final Pattern LINE_COL = Pattern.compile("line[:\\s]+(\\d+)[,\\s]+column[:\\s]+(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_ONLY = Pattern.compile("line[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE);
    /** BeanShell (zscript's default "java" language interpreter) reports a missing class this way, not as a
     * literal java.lang.ClassNotFoundException in the throwable chain -- see fixture (f), AC-6. */
    private static final Pattern MISSING_CLASS_IN_MESSAGE =
            Pattern.compile("[Cc]lass:?\\s+([\\w.$]+)\\s+not found in namespace");

    private ErrorMapper() {
    }

    public static RenderError map(String zulPath, Throwable t) {
        List<Throwable> chain = chainOf(t);

        Throwable classNotFound = findByType(chain, "java.lang.ClassNotFoundException");
        String missingClassInMessage = classNotFound == null ? findMissingClassInMessage(chain) : null;
        Integer[] pos = findPosition(chain);

        RenderPhase phase;
        String message;
        if (classNotFound != null) {
            phase = RenderPhase.COMPOSE;
            message = "Missing class: " + classNotFound.getMessage() + " (" + summarize(chain) + ")";
        } else if (missingClassInMessage != null) {
            phase = RenderPhase.COMPOSE;
            message = "Missing class: " + missingClassInMessage + " (" + summarize(chain) + ")";
        } else if (looksLikeParseError(chain)) {
            phase = RenderPhase.PARSE;
            message = summarize(chain);
        } else if (isUiException(chain)) {
            // A org.zkoss.zk.ui.UiException (or subclass) reaching here with no
            // missing-class/parse signal above was raised while ZK built the component
            // tree from an already-successfully-parsed document (e.g. "Unsupported
            // parent for row" from Row.beforeParentChanged) -- i.e. compose time.
            phase = RenderPhase.COMPOSE;
            message = summarize(chain);
        } else {
            phase = RenderPhase.UNKNOWN;
            message = summarize(chain);
        }
        return new RenderError(phase, message, zulPath, pos[0], pos[1]);
    }

    private static List<Throwable> chainOf(Throwable t) {
        List<Throwable> chain = new ArrayList<>();
        Throwable cur = t;
        while (cur != null && chain.size() < MAX_CHAIN_DEPTH && !chain.contains(cur)) {
            chain.add(cur);
            cur = cur.getCause();
        }
        return chain;
    }

    private static Throwable findByType(List<Throwable> chain, String fqcn) {
        for (Throwable t : chain) {
            if (fqcn.equals(t.getClass().getName())) return t;
        }
        return null;
    }

    private static String findMissingClassInMessage(List<Throwable> chain) {
        for (Throwable t : chain) {
            String msg = t.getMessage();
            if (msg == null) continue;
            Matcher m = MISSING_CLASS_IN_MESSAGE.matcher(msg);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /**
     * True if any throwable in the chain is a {@code org.zkoss.zk.ui.UiException} or a
     * subclass. Walks the exception's own Java class hierarchy (never the cause chain)
     * by fully-qualified name -- structural, not message-parsing -- so it needs no ZK
     * compile dependency (AC-2) and still catches subclasses such as
     * {@code WrongValueException}.
     */
    private static boolean isUiException(List<Throwable> chain) {
        for (Throwable t : chain) {
            for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
                if ("org.zkoss.zk.ui.UiException".equals(c.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean looksLikeParseError(List<Throwable> chain) {
        for (Throwable t : chain) {
            String name = t.getClass().getName().toLowerCase();
            String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (name.contains("parse") || name.contains("sax") || name.contains("xml") || msg.contains("parsing")) {
                return true;
            }
        }
        return false;
    }

    private static Integer[] findPosition(List<Throwable> chain) {
        for (Throwable t : chain) {
            String msg = t.getMessage();
            if (msg == null) continue;
            Matcher m = LINE_COL.matcher(msg);
            if (m.find()) {
                return new Integer[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
            }
        }
        for (Throwable t : chain) {
            String msg = t.getMessage();
            if (msg == null) continue;
            Matcher m = LINE_ONLY.matcher(msg);
            if (m.find()) {
                return new Integer[]{Integer.parseInt(m.group(1)), null};
            }
        }
        return new Integer[]{null, null};
    }

    private static String summarize(List<Throwable> chain) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(chain.size(), 4);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(" <- ");
            Throwable t = chain.get(i);
            sb.append(t.getClass().getName());
            if (t.getMessage() != null) sb.append(": ").append(t.getMessage());
        }
        return sb.toString();
    }
}
