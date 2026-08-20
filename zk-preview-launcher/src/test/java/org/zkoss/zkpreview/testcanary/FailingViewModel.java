package org.zkoss.zkpreview.testcanary;

/**
 * A controller that fails <em>while executing</em> -- the negative fixture P0-2's fail-soft
 * requirement (item 5) is about, as distinct from the AC-4 canaries, which fail at the
 * classloading step.
 *
 * <p>Deliberately a ViewModel and not a Composer: {@code UiFactory.newComposer} returns
 * {@code org.zkoss.zk.ui.util.Composer} (verified with {@code javap} on {@code AbstractUiFactory}
 * in zk-9.6.0.2), so a class implementing it cannot be compiled in this sourceSet, which has no
 * ZK compile dependency (build.gradle) and must not gain one. A ViewModel needs no ZK type at all.
 * The {@code apply=}/Composer half of the matrix is covered end to end in the agent-skill
 * showcase ({@code preview-fixtures/throwing-composer.zul}).
 */
public class FailingViewModel {

    public FailingViewModel() {
        throw new IllegalStateException("canary controller failure");
    }

    public String getGreeting() {
        return "NEVER-REACHED";
    }
}
