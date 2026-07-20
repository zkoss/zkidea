package org.zkoss.zkpreview.hooks;

import org.zkoss.zk.ui.util.GenericComposer;

/**
 * No-op composer substituted for both {@code apply="user.X"} and the auto-applied
 * MVVM composer (see {@link PreviewUiFactory}). {@link GenericComposer} already
 * implements every {@code Composer}/{@code ComposerExt} callback as a safe no-op;
 * this subclass exists only because {@code GenericComposer} itself is abstract.
 */
public class PreviewComposer extends GenericComposer {
    private static final long serialVersionUID = 1L;
}
