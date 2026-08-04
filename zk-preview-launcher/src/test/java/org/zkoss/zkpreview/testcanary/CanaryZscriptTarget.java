package org.zkoss.zkpreview.testcanary;

/** AC-4 negative control for fixture (f)'s zscript path (AC-6). */
public class CanaryZscriptTarget {
    public static volatile boolean WAS_CONSTRUCTED = false;

    public CanaryZscriptTarget() {
        WAS_CONSTRUCTED = true;
    }
}
