# Fix: `<zscript>` can't see the project's own classes (tasks/class-not-found.md)

## Root cause (confirmed, not a guess)

`demo.data.BigList` is compiled at `zkdemo/target/classes/demo/data/BigList.class` — the
**module output directory**. `ZulPreviewServerService.resolveTarget` hands the launcher only
`ZkClasspathFilter.filterLibraryJars(...)` (existing *regular files*) plus the module's resource
roots, so every directory — i.e. every compiled-output root — is dropped by construction. BeanShell
then cannot resolve the class and ZK aborts the whole page render.

This was **deliberate**: spec FR-7 calls the exclusion "the isolation boundary against user classes",
L-3 says a `<zscript>` naming a missing class "produces a structured COMPOSE failure rather than
rendering", and §6 lists it as permanent.

## Decision (user, this session)

Load the module's compiled output. The remaining isolation guarantee is the `PreviewUiFactory`
no-op hook, which is what actually blocks Composers/ViewModels — so MVVM placeholders (L-2) are
unchanged. Widened surface to document honestly: `<zscript>`, `use="user.X"` custom components,
custom EL/taglib functions and any class named by a `metainfo/zk/config.xml` **can now execute**.

## Steps

1. **RED** `ZkClasspathFilterTest.filterOutputDirectories…` — new filter keeps existing directories,
   drops files and non-existent paths. → verify: test fails to compile/pass before the impl.
2. **RED** `LauncherClasspathTest` — locks the assembly contract of a new package-visible, platform-free
   `ZulPreviewServerService.launcherClasspath(classpathEntries, productionClassEntries, resourceRootPaths)`:
   library jars first, then compiled-output dirs, then resource roots; SDK pseudo-entries dropped.
   → verify: red before the impl, green after.
3. **RED (end-to-end)** `ZulPreviewLauncherSeamTest` — compile a probe class into a temp "module
   output" dir, assemble the classpath through the production method, spawn the **real** launcher and
   request a new `manual-test/src/main/webapp/preview/zscript-user-class.zul` whose `<zscript>`
   instantiates that class. → verify: 500 "Missing class" before, 200 + rendered value after.
   (Replaces the old assertion that the output dir must never reach the launcher.)
4. **GREEN** `ZkClasspathFilter.filterOutputDirectories` + `ZulPreviewServerService`: a second,
   `productionOnly()` enumeration feeds the output dirs. The existing enumeration is left untouched,
   so the ZK-presence gate, the report summary and library-jar resolution (incl. `provided`-scope
   jars, which `productionOnly()` would drop) keep exactly today's behavior; `productionOnly()` is
   what keeps `target/test-classes` and test-scope jars off the render classpath.
   → verify: `withjdk.sh 17 ./gradlew test`.
5. **Docs** — spec FR-7 / FR-10a / L-2 / L-3 / §6, `feature_overview.md`, `zul-preview-feature.md`,
   `README.md`. Add the new limitation: classes are loaded once per helper JVM, so a rebuild is not
   picked up until the classpath signature changes (the signature hashes each entry's own
   size/mtime; editing a `.class` in place does not change its parent directory's mtime).
   → verify: no doc still claims output dirs are excluded / "your own code never runs".

## Review

All five steps done; `withjdk.sh 17 ./gradlew test` green (plugin + launcher).

- **Red proven, not assumed.** With the assembly temporarily weakened back to `filterLibraryJars`,
  the seam test fails against the real packaged launcher with
  `UiException: … Class or variable not found: preview.probe.ClasspathProbe` — the same shape as the
  reported `demo.data.BigList`. Restored, it renders the value.
- **One wrinkle found while writing the test**, both now documented in the test itself:
  `ToolProvider.getSystemJavaCompiler()` returns `null` in the IntelliJ test JVM (its system
  classloader is replaced), so the probe class is compiled by the `javac` binary; and ZK's JS
  encoder escapes `-` as `\-` in the widget JSON, so the probe value has to be alphanumeric.
- **Not fixed, documented instead (L-15):** a rebuild is not picked up by an already-running helper
  JVM. Hashing the output tree into the signature would fix it but would leak one idle JVM per
  rebuild (L-8), so it needs a server-eviction policy first.
- **Widened surface, stated in the spec §6 and the user guide:** `<zscript>`, `use="user.X"`,
  custom EL functions and any class named by the project's `metainfo/zk/config.xml` now execute
  the project's own bytecode in the helper JVM. ViewModels/Composers are unaffected — the
  `UiFactory` hook never resolves their class name.
