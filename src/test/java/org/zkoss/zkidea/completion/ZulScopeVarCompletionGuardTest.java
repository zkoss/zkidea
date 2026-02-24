package org.zkoss.zkidea.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ZulScopeVarCompletionContributor#fillCompletionVariants} guard conditions.
 *
 * <p>Each guard causes a silent early return with no lookup elements added.
 * The sentinel assertion in every test is that {@code result.withPrefixMatcher(any())} is
 * NEVER called, because it is the first operation performed after all guards pass.</p>
 *
 * <p>Guard sequence:
 * <ol>
 *   <li>originalFile instanceof XmlFile</li>
 *   <li>ZulDomUtil.isZKFile(psiFile)</li>
 *   <li>position.getParent() instanceof XmlAttributeValue</li>
 *   <li>ZulDomUtil.hasViewModel(position)</li>
 *   <li>position.getText().indexOf(DUMMY_IDENTIFIER) &gt;= 0</li>
 *   <li>BINDING_ROOT_PATTERN matches textBeforeCursor (no dot, recognised annotation)</li>
 * </ol>
 * </p>
 *
 * <p>Feature scenarios covered:
 * <ul>
 *   <li>"No scope variable suggestion in a plain XML file" (guards 1 &amp; 2)</li>
 *   <li>"No scope variable suggestion when no ViewModel is bound in the ZUL file" (guard 4)</li>
 *   <li>"No scope variable suggestion when the cursor is immediately after a dot" (guard 6)</li>
 *   <li>"No scope variable suggestion when a property name follows the dot" (guard 6)</li>
 *   <li>"No scope variable suggestion inside @converter and @validator" (guard 6)</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ZulScopeVarCompletionGuardTest {

    private final ZulScopeVarCompletionContributor contributor =
            new ZulScopeVarCompletionContributor();

    // ═══════════════════════════════════════════════════════════════════════════
    // Guard 1 — file is not an XmlFile at all
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void guard1_nonXmlFile_returnsEarly() {
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        PsiFile               psiFile  = mock(PsiFile.class); // NOT XmlFile

        when(parameters.getOriginalFile()).thenReturn(psiFile);

        contributor.fillCompletionVariants(parameters, result);

        verify(result, never()).withPrefixMatcher(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Guard 2 — file is XmlFile but not a ZUL file
    // Feature: "No scope variable suggestion in a plain XML file"
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void guard2_notZkFile_returnsEarly() {
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        XmlFile               psiFile  = mock(XmlFile.class);

        when(parameters.getOriginalFile()).thenReturn(psiFile);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(false);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Guard 3 — cursor is not inside an XmlAttributeValue
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void guard3_parentNotXmlAttributeValue_returnsEarly() {
        CompletionParameters parameters   = mock(CompletionParameters.class);
        CompletionResultSet   result      = mock(CompletionResultSet.class);
        XmlFile               psiFile    = mock(XmlFile.class);
        PsiElement            position   = mock(PsiElement.class);
        PsiElement            nonAttrVal = mock(PsiElement.class); // NOT XmlAttributeValue

        when(parameters.getOriginalFile()).thenReturn(psiFile);
        when(parameters.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(nonAttrVal);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Guard 4 — no ViewModel bound in the ZUL file
    // Feature: "No scope variable suggestion when no ViewModel is bound in the ZUL file"
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void guard4_noViewModel_returnsEarly() {
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        XmlFile               psiFile  = mock(XmlFile.class);
        PsiElement            position = mock(PsiElement.class);
        XmlAttributeValue     attrVal  = mock(XmlAttributeValue.class);

        when(parameters.getOriginalFile()).thenReturn(psiFile);
        when(parameters.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(attrVal);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(false);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Guard 5 — no DUMMY_IDENTIFIER in the element text (not a live-completion context)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void guard5_noDummyIdentifier_returnsEarly() {
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        XmlFile               psiFile  = mock(XmlFile.class);
        PsiElement            position = mock(PsiElement.class);
        XmlAttributeValue     attrVal  = mock(XmlAttributeValue.class);

        when(parameters.getOriginalFile()).thenReturn(psiFile);
        when(parameters.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(attrVal);
        when(position.getText()).thenReturn("@load(vm.name)"); // no DUMMY_IDENTIFIER

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(true);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Guard 6 — dot present before cursor: not at root position
    // Feature: "No scope variable suggestion when the cursor is immediately after a dot"
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void guard6_cursorImmediatelyAfterDot_returnsEarly() {
        // textBeforeCursor = "@load(vm." — dot present → BINDING_ROOT_PATTERN fails
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        XmlFile               psiFile  = mock(XmlFile.class);
        PsiElement            position = mock(PsiElement.class);
        XmlAttributeValue     attrVal  = mock(XmlAttributeValue.class);

        String text = "@load(vm." + CompletionUtilCore.DUMMY_IDENTIFIER;
        when(parameters.getOriginalFile()).thenReturn(psiFile);
        when(parameters.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(attrVal);
        when(position.getText()).thenReturn(text);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(true);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }

    // Feature: "No scope variable suggestion when a property name follows the dot"
    @Test
    void guard6_propertyNameAfterDot_returnsEarly() {
        // textBeforeCursor = "@load(vm.items" — dot in chain → BINDING_ROOT_PATTERN fails
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        XmlFile               psiFile  = mock(XmlFile.class);
        PsiElement            position = mock(PsiElement.class);
        XmlAttributeValue     attrVal  = mock(XmlAttributeValue.class);

        String text = "@load(vm.items" + CompletionUtilCore.DUMMY_IDENTIFIER;
        when(parameters.getOriginalFile()).thenReturn(psiFile);
        when(parameters.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(attrVal);
        when(position.getText()).thenReturn(text);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(true);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }

    // Feature: "No scope variable suggestion inside @converter and @validator"
    @Test
    void guard6_converterAnnotation_returnsEarly() {
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        XmlFile               psiFile  = mock(XmlFile.class);
        PsiElement            position = mock(PsiElement.class);
        XmlAttributeValue     attrVal  = mock(XmlAttributeValue.class);

        String text = "@converter(" + CompletionUtilCore.DUMMY_IDENTIFIER;
        when(parameters.getOriginalFile()).thenReturn(psiFile);
        when(parameters.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(attrVal);
        when(position.getText()).thenReturn(text);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(true);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }

    @Test
    void guard6_validatorAnnotation_returnsEarly() {
        CompletionParameters parameters = mock(CompletionParameters.class);
        CompletionResultSet   result    = mock(CompletionResultSet.class);
        XmlFile               psiFile  = mock(XmlFile.class);
        PsiElement            position = mock(PsiElement.class);
        XmlAttributeValue     attrVal  = mock(XmlAttributeValue.class);

        String text = "@validator(" + CompletionUtilCore.DUMMY_IDENTIFIER;
        when(parameters.getOriginalFile()).thenReturn(psiFile);
        when(parameters.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(attrVal);
        when(position.getText()).thenReturn(text);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(true);
            contributor.fillCompletionVariants(parameters, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
    }
}
