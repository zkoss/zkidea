# ZUL Preview — jakarta/javax mock de-duplication (review M1)

## L1 · Executive summary

The launcher renders against ZK on both `jakarta.servlet` (ZK 10.1.0) and `javax.servlet`
(ZK CE 9.6.0.2). Each servlet mock (`MockServletContext`, `MockHttpServletRequest`,
`MockHttpServletResponse`, `MockHttpSession`, `MockServletConfig`) existed as two byte-identical
copies apart from the `package` line and `jakarta`↔`javax` imports — nothing stopped a one-sided
edit from silently diverging (review M1). The two servlet APIs share no common supertype, so a
single class can't implement both; a real design-pattern fix is needed, not a parity checker.

**Pattern: Bridge.** Extract every piece of state and behaviour whose types are servlet-agnostic
(JDK-only) into a concrete, package-agnostic `*Core` class in `org.zkoss.zkpreview.mockcore`. Each
per-namespace mock becomes a thin **adapter** — `class MockX extends MockXCore implements <servlet
interface>` — where the inherited `Core` methods automatically satisfy every interface method with a
JDK signature, and the adapter body implements only the handful of methods whose signature (or body)
actually references a `jakarta`/`javax` type. The drift-prone logic (path-traversal containment,
zk.xml overlay, header lowercasing, response capture, session attributes) now lives in exactly one
place; the irreducible per-namespace part is the interface shell Java forces on us.

Progress: implementing (behaviour-preserving refactor; verified by the existing both-variant suite).

## L2 · Phase breakdown

| Core (shared, JDK-only) | Adapter keeps (servlet-typed only) |
|---|---|
| `MockServletContextCore` — webappDir, attributes, `resourceFile`+containment, zk.xml overlay, resource/attribute/init-param/version/log/classloader methods | dispatchers, servlet/filter/listener registration stubs, `NoOpServletRegistration`, session-cookie/tracking stubs |
| `MockHttpServletRequestCore` — servletPath/pathInfo/method, headers (lowercased), attributes, URI/URL build, all constant getters | session-derived (`getSession`, `getServletContext`, id), `getInputStream`, cookies, async/upgrade, `login`/`logout` (throw `ServletException`), `getParts` |
| `MockHttpServletResponseCore` — writer+byteBuffer capture, `getContent(Bytes)`, status, headers, `sendRedirect`, encodeURL, buffer stubs | `getOutputStream` (wraps the core buffer), `addCookie` |
| `MockHttpSessionCore` — id, times, attributes, value aliases, invalidate | `getServletContext`, `getSessionContext` |
| `MockServletConfigCore` — servletName, initParams | `getServletContext` |

`MockServletOutputStream` stays per-namespace (it `extends` the servlet `ServletOutputStream`
abstract class — no shared base possible) but is reduced to writing into a `ByteArrayOutputStream`
the response core owns. The two `*RenderEngine`s stay per-namespace: they construct the adapters and
reference `ServletRequest/Response/ContextEvent.class` for reflection — abstracting that would mean a
12-method `Object`-typed strategy, more complex and less safe than the ~26-line delta it removes.

**Acceptance gate:** `:zk-preview-launcher:test` stays BUILD SUCCESSFUL (baseline was green). The
both-variant tests — `MockServletContextTraversalTest`, `PathResolutionTest`, `RenderFidelityTest`,
`RealWorldSmokeTest`, `ZulSyntaxCorpusTest` — exercise the mocks through real ZK, so any behavioural
drift fails. Adapter public API (`MockServletContext(Path)`, `newSession()` → `MockHttpSession`, …)
is unchanged so the engines and tests compile untouched.

## L3 · Technical appendix

### Why not the lighter options
- **Parity test** enforces sameness but leaves two copies to hand-edit in lockstep — declined by the user.
- **Build-time codegen** dedups but is a build-system change on a release branch and moves the source of truth out of git.

### Irreducible remainder
The `@Override` interface shell (servlet-typed stubs + the one-line inheritance of JDK methods) cannot
be merged across `jakarta`/`javax` without reflection/proxies (fragile) or codegen. The Bridge removes
the *logic* duplication — the part where a silent one-sided edit is a real bug — which is M1's concern.

### Change log
- **Done.** Five `*Core` classes created in `org.zkoss.zkpreview.mockcore` (JDK-only imports, so
  `CoreIndependenceTest` still passes). All 10 mock adapters reduced to `extends *Core implements
  <servlet interface>` + only servlet-typed members. `MockServletOutputStream` reworked to write into
  the response core's `ByteArrayOutputStream`. The two `*RenderEngine`s and all adapter public API
  (`MockServletContext(Path)`, `MockHttpSession(MockServletContext)`, `newSession()`, …) are
  unchanged, so engines and tests compiled untouched.
- Boundary rule applied mechanically: **a method lives in the core iff its signature references only
  JDK types AND its body needs only core state.** The listener/role no-ops (`addListener`,
  `createListener`, `declareRoles` — signatures use only `java.util.EventListener`/`String`) moved to
  the context core. Request methods whose *body* touches the servlet-typed session
  (`getRequestedSessionId`, `changeSessionId`) stay in the adapter despite their `String` signature.
- Parity: each javax adapter is the exact `jakarta`→`javax` transform of its jakarta twin (verified:
  0 residual diff lines modulo the namespace token). No stray `jakarta` token remains under `javax/`.
- Verification: baseline `:zk-preview-launcher:test` green → after refactor still BUILD SUCCESSFUL
  (incl. ~96 real `ZulSyntaxCorpusTest` renders, `RenderFidelityTest`, `RealWorldSmokeTest`, both-variant
  `MockServletContextTraversalTest`, 6-way `PreviewHttpServerConcurrencyTest`, `JakartaSessionPerRenderTest`);
  plugin `:test` green. Behaviour-preserving.
