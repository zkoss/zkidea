package org.zkoss.zkidea.preview;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * The preview classes must <em>link</em> even when no JCEF class is reachable at all (issue #66).
 *
 * <p>Since IntelliJ 2026.2 (build 262) JCEF is no longer part of the JetBrains Runtime: it ships as
 * the bundled plugin {@code com.intellij.modules.jcef}, whose content modules own {@code org.cef.**}
 * and {@code com.intellij.ui.jcef.**}. A plugin classloader sees only the platform plus its declared
 * dependencies, so those packages can be missing outright -- and then a class that mentions them in
 * its own bytecode fails <em>verification</em>, before a single line of its code runs. That is what
 * broke #66: {@code ZulPreviewFileEditor} passed an anonymous {@code CefRequestHandlerAdapter} to
 * {@code JBCefClient.addRequestHandler(CefRequestHandler, …)}, so linking it forced a load of
 * {@code org.cef.handler.CefRequestHandler} (JVMS 4.10.1.2 {@code isJavaAssignable}) and threw
 * {@code NoClassDefFoundError} at {@code new ZulPreviewFileEditor(...)} -- with the result that
 * {@code .zul} files could not be opened at all, preview or no preview.
 *
 * <p>Nothing in the ordinary test run can catch that: the Gradle IntelliJ plugin puts every
 * {@code ideaIC/lib/*.jar} on the test classpath, and {@code lib/lib-client.jar} (a JetBrains-Client
 * jar the running IDE never loads) happens to contain {@code org.cef}. So these tests link the
 * classes through a loader that hides those packages, which is the only way to reproduce the
 * shipped-IDE classpath here.
 */
class PreviewEditorLinkageTest {

    @Test
    void previewEditor_linksWithNoJcefClassesReachable() {
        ClassLoader noJcef = new JcefHidingClassLoader();

        Class<?> linked = assertDoesNotThrow(
                () -> Class.forName(ZulPreviewFileEditor.class.getName(), true, noJcef),
                "the preview editor must link without JCEF -- otherwise .zul cannot be opened at all");

        assertNotSame(ZulPreviewFileEditor.class, linked,
                "must link a fresh copy under the hiding loader, not the already-loaded test one");
    }

    @Test
    void previewEditorProvider_linksWithNoJcefClassesReachable() {
        ClassLoader noJcef = new JcefHidingClassLoader();

        assertDoesNotThrow(
                () -> Class.forName(ZulPreviewFileEditorProvider.class.getName(), true, noJcef),
                "the editor provider must link without JCEF");
    }

    /**
     * The availability probe is the one place allowed to touch {@code JBCefApp}, so it must survive
     * the class being unreachable and turn that into a diagnosis rather than an error.
     */
    @Test
    void availabilityProbe_reportsADiagnosisInsteadOfThrowing() throws Exception {
        ClassLoader noJcef = new JcefHidingClassLoader();
        Class<?> availability = Class.forName(JcefAvailability.class.getName(), true, noJcef);
        Method probe = availability.getDeclaredMethod("probe");
        probe.setAccessible(true);

        Object diagnosis = assertDoesNotThrow(() -> probe.invoke(null),
                "probing for JCEF must never propagate a LinkageError");

        assertNotNull(diagnosis, "no JCEF classes means JCEF is unusable -- that needs a diagnosis");
        Method explanation = diagnosis.getClass().getDeclaredMethod("getExplanation");
        explanation.setAccessible(true);
        Object text = explanation.invoke(diagnosis);
        assertNotNull(text);
        assertFalse(String.valueOf(text).isBlank(),
                "the user must be told why the preview is unavailable");

        // The missing classes must be read as missing classes -- not as "JCEF is here but broke",
        // which is what a blanket LinkageError catch would report (ExceptionInInitializerError is
        // a LinkageError too). Which of the two class-missing reasons applies depends on the JVM
        // running this test, exactly as it does in a real IDE.
        Method reason = diagnosis.getClass().getDeclaredMethod("getReason");
        reason.setAccessible(true);
        String vendor = String.valueOf(System.getProperty("java.vendor")).toLowerCase(Locale.ROOT);
        String expected = vendor.contains("jetbrains") ? "JCEF_PLUGIN_UNAVAILABLE" : "BOOT_JDK_NO_JCEF";
        assertEquals(expected, String.valueOf(reason.invoke(diagnosis)),
                "unreachable JCEF classes must be diagnosed as missing, with the remedy that fits "
                        + "this runtime (java.vendor=" + System.getProperty("java.vendor") + ")");
    }

    /**
     * Loads {@code org.zkoss.zkidea.preview.*} itself (so their verification really happens here)
     * while refusing every JCEF package, and delegates everything else -- the IntelliJ platform,
     * the JDK -- to the test classloader.
     */
    private static final class JcefHidingClassLoader extends ClassLoader {

        private static final String[] HIDDEN_PACKAGES = {"org.cef.", "com.intellij.ui.jcef."};
        private static final String PREVIEW_PACKAGE = "org.zkoss.zkidea.preview.";

        JcefHidingClassLoader() {
            super(PreviewEditorLinkageTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            for (String hidden : HIDDEN_PACKAGES) {
                if (name.startsWith(hidden)) {
                    throw new ClassNotFoundException(name + " (hidden: this IDE has no JCEF plugin)");
                }
            }
            if (!name.startsWith(PREVIEW_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                return loaded != null ? loaded : define(name, resolve);
            }
        }

        private Class<?> define(String name, boolean resolve) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream bytecode = getParent().getResourceAsStream(resource)) {
                if (bytecode == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = bytecode.readAllBytes();
                Class<?> defined = defineClass(name, bytes, 0, bytes.length);
                if (resolve) {
                    resolveClass(defined);
                }
                return defined;
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
