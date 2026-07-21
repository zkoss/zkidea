package org.zkoss.zkpreview.hooks;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.util.GenericComposer;

/**
 * Substituted for both {@code apply="user.X"} and the auto-applied MVVM composer
 * (see {@link PreviewUiFactory}). {@link GenericComposer} already implements every
 * {@code Composer}/{@code ComposerExt} callback as a safe no-op, so no user code runs.
 *
 * <p>Its one active job is timing: {@link #doAfterCompose} fires once the applied
 * subtree is fully composed -- the same point at which the real MVVM binder would set
 * models -- which is exactly when {@link PlaceholderInjector#injectModels} must run so a
 * model-bound data component's explicit {@code <rows>}/{@code <listhead>} already exists
 * (injecting earlier makes ZK auto-create a duplicate and fail). Text placeholders are
 * handled separately by the {@code PlaceholderInjector} UiLifeCycle listener.
 */
public class PreviewComposer extends GenericComposer {
    private static final long serialVersionUID = 1L;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        PlaceholderInjector.injectModels(comp);
    }
}
