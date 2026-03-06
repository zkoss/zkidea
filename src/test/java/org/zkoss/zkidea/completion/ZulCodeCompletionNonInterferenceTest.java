package org.zkoss.zkidea.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import static org.mockito.Mockito.*;

/**
 * Tests that {@link MVVMAnnotationCompletionProvider} and
 * {@link ZulScopeVarCompletionContributor} do NOT suppress or interfere with
 * the native XML schema-based completion for ZUL tag names and attribute names.
 *
 * <p>Both contributors are CompletionContributors for the XML language. They must only
 * activate inside {@code XmlAttributeValue} positions. At tag-name and attribute-name
 * positions the cursor's PSI parent is {@code XmlTag} or {@code XmlAttribute} — the
 * contributors must return early, neither adding items nor calling {@code stopHere()}.
 *
 * <p>Feature file: {@code zul-code-completion.feature} — "non-suppression" scenarios.
 *
 * <p>Stub precision note: each test stubs ONLY the methods that the contributor code
 * actually reaches for that specific execution path, satisfying Mockito strict-stubbing.
 * See the control-flow analysis in method-level JavaDoc comments.
 */
@ExtendWith(MockitoExtension.class)
class ZulCodeCompletionNonInterferenceTest {

    private final MVVMAnnotationCompletionProvider mvvmProvider =
            new MVVMAnnotationCompletionProvider();
    private final ZulScopeVarCompletionContributor scopeVarProvider =
            new ZulScopeVarCompletionContributor();

    // ═══════════════════════════════════════════════════════════════════════════
    // MVVMAnnotationCompletionProvider — non-ZK file guard
    // Code path: getOriginalFile() → isZKFile=false → return (getPosition never called)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZUL tag completion works in a file that has no ViewModel" — non-ZK guard.
     * When the file is not a ZK file, the contributor must return before accessing the cursor
     * position element. No items, no stopHere().
     */
    @Test
    void mvvm_nonZkFile_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        // Only getOriginalFile() is reached before the isZKFile guard exits
        when(params.getOriginalFile()).thenReturn(psiFile);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(false);

            mvvmProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).addElement(any());
        verify(result, never()).stopHere();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MVVMAnnotationCompletionProvider — tag-name position, no ViewModel
    // Code path: getOriginalFile() → isZKFile=true → getPosition() → hasViewModel=false → return
    // (getParent() never called because hasViewModel returns false)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZUL tag completion works in a file that has no ViewModel".
     * isZKFile=true but hasViewModel=false → contributor returns without touching the result.
     */
    @Test
    void mvvm_tagNamePosition_noViewModel_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);
        // position.getParent() is NOT stubbed — not called when hasViewModel=false

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(false);

            mvvmProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).addElement(any());
        verify(result, never()).stopHere();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MVVMAnnotationCompletionProvider — tag-name position, WITH ViewModel
    // Code path: getOriginalFile() → isZKFile=true → getPosition() → hasViewModel=true
    //            → getText() → getParent()=XmlTag → instanceof XmlAttributeValue = false → return
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZUL tag completion works in a MVVM-enabled file outside an attribute value".
     * hasViewModel=true but the PSI parent is XmlTag (tag-name position, not attribute value).
     * The inner {@code if (xmlText instanceof XmlAttributeValue)} is false → contributor exits
     * without adding annotation items and without calling stopHere().
     */
    @Test
    void mvvm_tagNamePosition_withViewModel_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        XmlTag parentTag = mock(XmlTag.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);
        // getText() is called before getParent(); return any non-null string
        when(position.getText()).thenReturn("win");
        when(position.getParent()).thenReturn(parentTag); // XmlTag, NOT XmlAttributeValue

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(true);

            mvvmProvider.fillCompletionVariants(params, result);
        }

        // Must NOT add annotation items at tag-name position
        verify(result, never()).addElement(any());
        // Must NOT block native XML schema completion
        verify(result, never()).stopHere();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MVVMAnnotationCompletionProvider — attribute-name position, no ViewModel
    // Code path: same as tagNamePosition_noViewModel (getParent not reached)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZUL attribute completion works in a file that has no ViewModel".
     */
    @Test
    void mvvm_attrNamePosition_noViewModel_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(false);

            mvvmProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).addElement(any());
        verify(result, never()).stopHere();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MVVMAnnotationCompletionProvider — attribute-name position, WITH ViewModel
    // Code path: getText() called, getParent() → XmlAttribute (not XmlAttributeValue) → return
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZUL attribute completion works in a MVVM-enabled file".
     * Cursor is on the attribute NAME (e.g. typing {@code "wi"} for {@code "width"}).
     * PSI parent is {@code XmlAttribute}, not {@code XmlAttributeValue}.
     */
    @Test
    void mvvm_attrNamePosition_withViewModel_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        XmlAttribute parentAttr = mock(XmlAttribute.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);
        when(position.getText()).thenReturn("wi");
        when(position.getParent()).thenReturn(parentAttr); // XmlAttribute, NOT XmlAttributeValue

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(true);

            mvvmProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).addElement(any());
        verify(result, never()).stopHere();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Critical: attribute VALUE position, no ViewModel
    // MVVMAnnotationCompletionProvider: isZKFile=true → getPosition() → hasViewModel=false → return
    // (getParent NOT called — hasViewModel guard exits before reaching it)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * When the cursor IS inside an attribute value but there is no ViewModel,
     * the MVVM contributor must NOT add annotation suggestions and must NOT suppress
     * native XML completion (e.g. enum completions for {@code visible="true|false"}).
     */
    @Test
    void mvvm_attrValuePosition_noViewModel_addsNoItemsAndDoesNotStopHere() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);
        // position.getParent() NOT stubbed — not reached when hasViewModel=false

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(false);

            mvvmProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).addElement(any());
        verify(result, never()).stopHere();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZulScopeVarCompletionContributor — non-ZK file guard
    // Code path: getOriginalFile() → isZKFile=false → return (getPosition never called)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void scopeVar_nonZkFile_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(false);

            scopeVarProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
        verify(result, never()).addElement(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZulScopeVarCompletionContributor — tag-name position (parent = XmlTag)
    // Code path: getOriginalFile() → isZKFile=true → getPosition() → getParent()=XmlTag
    //            → instanceof XmlAttributeValue = false → return
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZUL tag completion works in a file that has no ViewModel".
     * ScopeVar contributor: position.getParent() is XmlTag → not an XmlAttributeValue → returns early.
     */
    @Test
    void scopeVar_tagNamePosition_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        XmlTag parentTag = mock(XmlTag.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(parentTag); // XmlTag → fails XmlAttributeValue check

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            // hasViewModel() is NOT reached — early return after instanceof check

            scopeVarProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
        verify(result, never()).addElement(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZulScopeVarCompletionContributor — attribute-name position (parent = XmlAttribute)
    // Code path: getPosition() → getParent()=XmlAttribute → instanceof XmlAttributeValue = false → return
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature: "ZUL attribute completion works in a file that has no ViewModel".
     * Cursor is on an attribute NAME — parent is XmlAttribute, not XmlAttributeValue.
     */
    @Test
    void scopeVar_attrNamePosition_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        XmlAttribute parentAttr = mock(XmlAttribute.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(parentAttr); // XmlAttribute → fails check

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);

            scopeVarProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
        verify(result, never()).addElement(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZulScopeVarCompletionContributor — attribute value position, no ViewModel
    // Code path: getPosition() → getParent()=XmlAttributeValue → hasViewModel=false → return
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Cursor IS inside an attribute value but no ViewModel → returns early.
     * Must NOT call withPrefixMatcher() or addElement().
     */
    @Test
    void scopeVar_attrValuePosition_noViewModel_addsNoItems() {
        XmlFile psiFile = mock(XmlFile.class);
        PsiElement position = mock(PsiElement.class);
        XmlAttributeValue attrValue = mock(XmlAttributeValue.class);
        CompletionParameters params = mock(CompletionParameters.class);
        CompletionResultSet result = mock(CompletionResultSet.class);

        when(params.getOriginalFile()).thenReturn(psiFile);
        when(params.getPosition()).thenReturn(position);
        when(position.getParent()).thenReturn(attrValue);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(psiFile)).thenReturn(true);
            util.when(() -> ZulDomUtil.hasViewModel(position)).thenReturn(false);

            scopeVarProvider.fillCompletionVariants(params, result);
        }

        verify(result, never()).withPrefixMatcher(anyString());
        verify(result, never()).addElement(any());
    }
}
