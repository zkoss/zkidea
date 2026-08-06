package org.zkoss.zkpreview.hooks;

import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.http.SimpleUiFactory;
import org.zkoss.zk.ui.metainfo.PageDefinition;
import org.zkoss.zk.ui.sys.RequestInfo;
import org.zkoss.zk.ui.util.Composer;

import java.util.regex.Pattern;

/**
 * Registered via zk.xml {@code <system-config><ui-factory-class>} (ZK's documented
 * customization point; default is {@code org.zkoss.zk.ui.http.SimpleUiFactory}).
 *
 * <p>{@code ComponentInfo.resolveComposer} resolves BOTH an explicit
 * {@code apply="user.X"} AND the auto-applied MVVM composer (the FQCN stored by
 * the Parser for {@code viewModel=...}, defaulting to {@code org.zkoss.bind.BindComposer})
 * through this exact same {@code UiFactory.newComposer(Page, String)} call. Overriding
 * it to always return a no-op composer -- without ever delegating to the default
 * implementation, which resolves the class name via {@code Page.resolveClass} -- blocks
 * both paths from ever loading a user class, in one hook.
 *
 * <p>Design alternative considered and ruled out: a {@code BindComposer} subclass overriding
 * {@code initViewModel} (installed via the {@code org.zkoss.bind.defaultComposer.class} library
 * property) would have preserved full binder fidelity against a stub ViewModel. It is not
 * possible -- {@code javap} against both zkbind-9.6.0.2.jar and zkbind-10.1.0-jakarta.jar shows
 * {@code BindComposer.initViewModel} is PRIVATE, so it cannot be overridden by subclassing.
 * This single {@code UiFactory} hook is therefore the whole isolation mechanism.
 *
 * <p>{@link #getPageDefinition} additionally guards a second leak path: a shadow element such
 * as {@code <apply templateURI="...">} resolves an
 * annotation-valued attribute (e.g. {@code @load(vm.templatePath)}) by handing the
 * raw, unresolved annotation text to {@code Execution.createComponents(uri, ...)} as
 * a literal page path -- either because ZK's compiler never recognized a half-typed
 * annotation as valid syntax at all (manual-test/template-uri-nav.zul's IDE-completion
 * fixture: a missing closing paren falls through to a literal property assignment), or
 * because the preview's no-op composer above never runs a {@code Binder} to resolve a
 * well-formed one. Either way that literal reaches {@code UiFactory.getPageDefinition}
 * (see {@code ExecutionImpl#getPageDefinition}, which throws
 * {@code UiException("Page not found: " + uri)} when this method returns null) before
 * ZK ever gets a chance to treat it as "unresolved". Recognizing the annotation-attempt
 * shape here and handing back an empty synthesized page (rather than delegating to the
 * default file lookup, which would return null) reproduces real ZK's own "absent bound
 * value" outcome: the {@code <apply>} inserts nothing and the rest of the page still
 * renders. A genuinely missing literal path (no leading {@code @name(}) is unaffected
 * and still fails with the existing structured "Page not found" error.
 */
public class PreviewUiFactory extends SimpleUiFactory {

    /** Mirrors {@code org.zkoss.zkpreview.IsolationMode.SYSTEM_PROPERTY}; duplicated as a
     * literal so this hooks sourceSet stays compiled against ZK jars only (no
     * cross-sourceSet compile dependency on the main launcher). */
    private static final String ISOLATION_PROPERTY = "zkpreview.isolation";

    /** Matches a path whose last segment starts with an unresolved {@code @name(}
     * binding-annotation attempt, complete or half-typed. */
    private static final Pattern UNRESOLVED_ANNOTATION_PATH = Pattern.compile("(^|/)@\\w+\\(");

    /** A minimal, always-valid ZUML document with no components -- synthesized in place
     * of a real page lookup so an unresolved annotation contributes nothing instead of
     * failing "Page not found". */
    private static final String EMPTY_PAGE_ZUML = "<zk/>";

    private static boolean isolationEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(ISOLATION_PROPERTY));
    }

    @Override
    public Composer newComposer(Page page, String clsnm) throws ClassNotFoundException {
        if (isolationEnabled()) {
            return new PreviewComposer();
        }
        return super.newComposer(page, clsnm);
    }

    @Override
    public Composer newComposer(Page page, Class cls) {
        if (isolationEnabled()) {
            return new PreviewComposer();
        }
        return super.newComposer(page, cls);
    }

    @Override
    public PageDefinition getPageDefinition(RequestInfo ri, String path) {
        if (path != null && UNRESOLVED_ANNOTATION_PATH.matcher(path).find()) {
            return getPageDefinitionDirectly(ri, EMPTY_PAGE_ZUML, "zul");
        }
        return super.getPageDefinition(ri, path);
    }
}
