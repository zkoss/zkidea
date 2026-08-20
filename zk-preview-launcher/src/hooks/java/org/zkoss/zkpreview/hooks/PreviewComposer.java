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
 * (injecting earlier makes ZK auto-create a duplicate and fail). Text placeholders are set by
 * the {@code PlaceholderInjector} UiLifeCycle listener as components attach, but their dim
 * styling is applied here -- post-composition -- because a style set at attach time does not
 * survive to the serialized page (see {@link PlaceholderInjector#dimPlaceholders}).
 *
 * <p>Installed only while isolation is on (see {@link IsolationScope}): under
 * {@code --isolation off} / {@code --run-controllers} the {@link PreviewUiFactory} overrides
 * delegate to the default resolution, so the project's own composer is constructed instead of
 * this one and {@link PlaceholderInjector#injectModels}/{@link PlaceholderInjector#dimPlaceholders}
 * never run. That is why the controllers-on column of the P0-2 placeholder matrix has no
 * placeholder rows and no dimmed expression text at all.
 */
public class PreviewComposer extends GenericComposer {
    private static final long serialVersionUID = 1L;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        PlaceholderInjector.injectModels(comp);
        PlaceholderInjector.dimPlaceholders(comp);
    }
}
