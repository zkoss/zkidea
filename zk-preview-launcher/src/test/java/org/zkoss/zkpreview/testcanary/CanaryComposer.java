package org.zkoss.zkpreview.testcanary;

/**
 * AC-4 negative control for the {@code apply="..."} path. Deliberately a plain
 * class (not implementing ZK's {@code Composer}) -- resolution fails at the
 * classloading step itself (the point being tested), never reaching the point
 * where interface conformance would matter.
 */
public class CanaryComposer {
    public static volatile boolean WAS_CONSTRUCTED = false;

    public CanaryComposer() {
        WAS_CONSTRUCTED = true;
    }
}
