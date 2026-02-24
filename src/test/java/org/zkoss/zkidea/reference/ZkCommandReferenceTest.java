package org.zkoss.zkidea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttributeValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZkCommandReference#resolve()} and
 * {@link ZkCommandReference#getCommandName(PsiMethod)}.
 *
 * <p>Mapped to the navigation scenarios in command-binding-navigation.feature.
 *
 * <p>No IntelliJ platform initialisation is needed — all PSI types are interfaces
 * and are provided as plain Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class ZkCommandReferenceTest {

    private static final String CMD_ANN  = "org.zkoss.bind.annotation.Command";
    private static final String GCMD_ANN = "org.zkoss.bind.annotation.GlobalCommand";

    // ─── factory helpers ────────────────────────────────────────────────────

    /** Creates a {@link ZkCommandReference} with a dummy element/range. */
    private ZkCommandReference ref(PsiClass vmClass, String commandName) {
        XmlAttributeValue attr = mock(XmlAttributeValue.class);
        return new ZkCommandReference(attr, new TextRange(0, commandName.length()),
                vmClass, commandName);
    }

    /**
     * Mocks a {@link PsiMethod} annotated with {@code @Command}.
     *
     * @param methodName     Java method name
     * @param annotationText the literal text of the {@code value} attribute
     *                       (e.g. {@code "\"save\""} for {@code @Command(value="save")}),
     *                       or {@code null} to simulate no explicit annotation value
     */
    private PsiMethod commandMethod(String methodName, String annotationText) {
        PsiMethod method = mock(PsiMethod.class);
        lenient().when(method.getName()).thenReturn(methodName);

        PsiAnnotation ann = mock(PsiAnnotation.class);
        when(ann.getQualifiedName()).thenReturn(CMD_ANN);
        when(method.getAnnotations()).thenReturn(new PsiAnnotation[]{ann});

        if (annotationText != null) {
            PsiAnnotationMemberValue val = mock(PsiAnnotationMemberValue.class);
            when(val.getText()).thenReturn(annotationText);
            when(ann.findDeclaredAttributeValue("value")).thenReturn(val);
        } else {
            when(ann.findDeclaredAttributeValue("value")).thenReturn(null);
        }
        return method;
    }

    /** Mocks a {@link PsiMethod} annotated with {@code @GlobalCommand} (no explicit value). */
    private PsiMethod globalCommandMethod(String methodName) {
        PsiMethod method = mock(PsiMethod.class);
        when(method.getName()).thenReturn(methodName);

        PsiAnnotation ann = mock(PsiAnnotation.class);
        when(ann.getQualifiedName()).thenReturn(GCMD_ANN);
        when(method.getAnnotations()).thenReturn(new PsiAnnotation[]{ann});
        when(ann.findDeclaredAttributeValue("value")).thenReturn(null);
        return method;
    }

    /** Mocks a {@link PsiMethod} with no {@code @Command}/{@code @GlobalCommand} annotation. */
    private PsiMethod plainMethod() {
        PsiMethod method = mock(PsiMethod.class);
        when(method.getAnnotations()).thenReturn(new PsiAnnotation[0]);
        return method;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getCommandName — static utility
    // ═══════════════════════════════════════════════════════════════════════

    // Feature: Navigate to a command method — single-quoted / double-quoted string
    // (both rely on getCommandName falling back to method name when no value attr)

    @Test
    void getCommandName_commandAnnotationWithNoValue_returnsMethodName() {
        // @Command  (no explicit value) → effective name == method name
        PsiMethod method = commandMethod("saveItem", null);
        assertEquals("saveItem", ZkCommandReference.getCommandName(method));
    }

    // Feature: Navigate resolves via the annotation value, not the method name
    @Test
    void getCommandName_commandAnnotationWithExplicitValue_returnsAnnotationValue() {
        // @Command(value="save") on method "persistItem" → effective name == "save"
        PsiMethod method = commandMethod("persistItem", "\"save\"");
        assertEquals("save", ZkCommandReference.getCommandName(method));
    }

    @Test
    void getCommandName_commandAnnotationWithSingleQuotedValue_returnsAnnotationValue() {
        // annotation value stored with single quotes (edge case)
        PsiMethod method = commandMethod("persistItem", "'save'");
        assertEquals("save", ZkCommandReference.getCommandName(method));
    }

    // Feature: Navigate to a global command method
    @Test
    void getCommandName_globalCommandAnnotation_returnsMethodName() {
        PsiMethod method = globalCommandMethod("broadcast");
        assertEquals("broadcast", ZkCommandReference.getCommandName(method));
    }

    @Test
    void getCommandName_noCommandAnnotation_returnsNull() {
        // Plain method (e.g. getter) with no @Command / @GlobalCommand
        PsiMethod method = plainMethod();
        assertNull(ZkCommandReference.getCommandName(method));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // resolve()
    // ═══════════════════════════════════════════════════════════════════════

    // Feature: No navigation when the ZUL file has no ViewModel bound
    // (vmClass is null when no ViewModel can be resolved)
    @Test
    void resolve_vmClassNull_returnsNull() {
        PsiElement result = ref(null, "saveItem").resolve();
        assertNull(result);
    }

    // Feature: Navigate to a command method — single-quoted string
    @Test
    void resolve_matchesByCommandName_returnsSaveItemMethod() {
        PsiMethod saveItemMethod = commandMethod("saveItem", null);
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{saveItemMethod});

        PsiElement result = ref(vmClass, "saveItem").resolve();

        assertSame(saveItemMethod, result);
    }

    // Feature: Navigate to a command method — double-quoted string
    // (same resolve() logic; double-quote handling is upstream in the provider)
    @Test
    void resolve_doubleQuotedCommandName_resolvesSameMethod() {
        PsiMethod saveItemMethod = commandMethod("saveItem", null);
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{saveItemMethod});

        // commandName is the bare name extracted from the expression — no quotes
        assertSame(saveItemMethod, ref(vmClass, "saveItem").resolve());
    }

    // Feature: Navigate resolves via the annotation value, not the method name
    @Test
    void resolve_commandNameMatchesAnnotationValue_returnsPersistItemMethod() {
        // ViewModel has: persistItem @Command(value="save")
        // Binding uses @command('save') → commandName="save" → must find persistItem
        PsiMethod persistItem = commandMethod("persistItem", "\"save\"");
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{persistItem});

        PsiElement result = ref(vmClass, "save").resolve();

        assertSame(persistItem, result);
    }

    // Feature: Navigate to a global command method
    @Test
    void resolve_globalCommandMethod_returnsBroadcastMethod() {
        PsiMethod broadcastMethod = globalCommandMethod("broadcast");
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{broadcastMethod});

        PsiElement result = ref(vmClass, "broadcast").resolve();

        assertSame(broadcastMethod, result);
    }

    // Feature: Navigate to a command when extra key-value arguments are present
    // (the commandName passed to the reference is already stripped; resolve is identical)
    @Test
    void resolve_commandWithExtraArguments_resolvesCommandNameOnly() {
        PsiMethod saveItemMethod = commandMethod("saveItem", null);
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{saveItemMethod});

        // commandName is always just the bare name — extra args are not stored here
        assertSame(saveItemMethod, ref(vmClass, "saveItem").resolve());
    }

    // Feature: Navigate from a before-guard / after-guard command
    @Test
    void resolve_beforeGuardCommandName_resolvesToValidateMethod() {
        PsiMethod validateMethod = commandMethod("validate", null);
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{validateMethod});

        assertSame(validateMethod, ref(vmClass, "validate").resolve());
    }

    @Test
    void resolve_afterGuardCommandName_resolvesToCommitMethod() {
        PsiMethod commitMethod = commandMethod("commit", null);
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{commitMethod});

        assertSame(commitMethod, ref(vmClass, "commit").resolve());
    }

    @Test
    void resolve_commandNameNotFound_returnsNull() {
        // vmClass has no method matching the requested command name
        PsiMethod unrelated = commandMethod("unrelated", null);
        PsiClass vmClass = mock(PsiClass.class);
        when(vmClass.getAllMethods()).thenReturn(new PsiMethod[]{unrelated});

        assertNull(ref(vmClass, "missing").resolve());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getVariants() — null-safety guard
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getVariants_vmClassNull_returnsEmptyArray() {
        Object[] variants = ref(null, "saveItem").getVariants();
        assertNotNull(variants);
        assertEquals(0, variants.length);
    }
}
