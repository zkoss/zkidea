package org.zkoss.zkpreview;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zkoss.zkpreview.testutil.Variants;
import org.zkoss.zkpreview.testutil.ZkClasspathResolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every EL implicit object listed in ZK's own ZUML Reference (its
 * "Implicit Objects / Predefined Variables" chapter), exercised through the production launcher
 * render path on BOTH servlet variants ({@link Variants#both()}).
 *
 * <p>The single fixture {@code syntax/el-implicit-objects.zul} prints, per object, a unique token
 * immediately followed by {@code ${obj != null}} so the rendered value reads {@code ioXxxtrue} or
 * {@code ioXxxfalse} (the {@code el-empty} concatenation pattern; alphanumeric, hyphen-free per
 * lesson #14). This asserts what the preview genuinely resolves:
 *
 * <ul>
 *   <li><b>24 of 25 resolve to a live object.</b> Unlike MVVM {@code @load} (which stays a
 *       placeholder because the ViewModel is never instantiated), implicit objects are produced by
 *       ZK's real page-evaluation runtime -- the launcher renders through a genuine
 *       {@code Execution}/{@code Desktop}/{@code Page}. {@code desktop.id} renders a real generated
 *       id (prefix {@code z_}), proving these are live objects, not stubs.</li>
 *   <li><b>{@code event} is the one exception (resolves to null).</b> The reference states
 *       {@code event} is "available for the event listener only"; page-evaluation render dispatches
 *       no event, so {@code ${event != null}} is correctly {@code false}. This is faithful
 *       behavior, not a preview gap.</li>
 * </ul>
 *
 * <p>Verified identical on javax (ZK 9.6) and jakarta (ZK 10). Note: {@code .class} reads (e.g.
 * {@code ${x.class.simpleName}}) are intentionally absent -- ZK 10's EL rejects {@code class} as an
 * identifier and aborts the whole page, so liveness is proven via {@code desktop.id} instead.
 */
class ImplicitObjectsElTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures");
    private static final String FIXTURE = "/syntax/el-implicit-objects.zul";

    /** One implicit object: its reference name, the planted token, and whether it resolves non-null. */
    record ImplicitObj(String name, String token, boolean resolves) {
        String expected() { return token + resolves; } // e.g. "ioDesktoptrue" / "ioEventfalse"
        @Override public String toString() { return name; }
    }

    /** Every object from the ZUML Reference table that carries a {@code ${obj != null}} probe. */
    private static final List<ImplicitObj> OBJECTS = List.of(
            // live component / runtime objects
            new ImplicitObj("self", "ioSelf", true),
            new ImplicitObj("page", "ioPage", true),
            new ImplicitObj("desktop", "ioDesktop", true),
            new ImplicitObj("execution", "ioExec", true),
            new ImplicitObj("session", "ioSession", true),
            new ImplicitObj("pageContext", "ioPageCtx", true),
            new ImplicitObj("spaceOwner", "ioSpaceOwner", true),
            // scope maps
            new ImplicitObj("applicationScope", "ioAppScope", true),
            new ImplicitObj("sessionScope", "ioSessScope", true),
            new ImplicitObj("desktopScope", "ioDeskScope", true),
            new ImplicitObj("requestScope", "ioReqScope", true),
            new ImplicitObj("pageScope", "ioPageScope", true),
            new ImplicitObj("componentScope", "ioCompScope", true),
            new ImplicitObj("spaceScope", "ioSpaceScope", true),
            // request-derived maps
            new ImplicitObj("param", "ioParam", true),
            new ImplicitObj("paramValues", "ioParamVals", true),
            new ImplicitObj("cookie", "ioCookie", true),
            new ImplicitObj("header", "ioHeader", true),
            new ImplicitObj("headerValues", "ioHeaderVals", true),
            // i18n labels + browser info
            new ImplicitObj("labels", "ioLabels", true),
            new ImplicitObj("zk", "ioZk", true),
            // include-only argument map: non-null (empty) even at top level
            new ImplicitObj("arg", "ioArg", true),
            // listener-only: no event at page-evaluation render -> correctly null
            new ImplicitObj("event", "ioEvent", false)
    );

    static Stream<Variants.Named> variants() { return Variants.both(); }

    @ParameterizedTest(name = "implicit objects [{0}]")
    @MethodSource("variants")
    void allImplicitObjectsResolveExceptEvent(Variants.Named variant) throws Exception {
        ZkClasspathResolver.Resolution res = variant.resolve();
        Assumptions.assumeTrue(res.jars != null, "skip: " + res.skipReason);

        try (RenderEngine engine = RenderEngineFactory.create(res.jars, FIXTURES, null)) {
            RenderResult r = engine.renderZul(FIXTURE);
            assertTrue(r.isSuccess(), () -> "expected SUCCESS, got: " + describeFailure(r));
            String html = r.getHtml();

            // 23 ${obj != null} probes: 22 resolve true, event resolves false.
            for (ImplicitObj o : OBJECTS) {
                assertTrue(html.contains(o.expected()),
                        () -> "implicit object '" + o.name() + "': expected '" + o.expected() + "' in: " + html);
            }

            // each + forEachStatus need a forEach context: iteration yields the item and 0-based index.
            assertTrue(html.contains("ioEachPioIdx0"),
                    () -> "each/forEachStatus row 1 ('ioEachPioIdx0') missing in: " + html);
            assertTrue(html.contains("ioEachQioIdx1"),
                    () -> "each/forEachStatus row 2 ('ioEachQioIdx1') missing in: " + html);

            // Liveness proof: desktop is a real object, not a stub -- its id has ZK's 'z_' prefix.
            assertTrue(html.contains("vDesktopId=[z_"),
                    () -> "expected a live desktop id ('vDesktopId=[z_...') in: " + html);
        }
    }

    private static String describeFailure(RenderResult r) {
        return r.isSuccess() ? "(success)" : r.getError().toJson();
    }
}
