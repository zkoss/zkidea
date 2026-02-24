package org.zkoss.zkidea.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link ZulScopeVarCompletionContributor#fillCompletionVariants} content behaviour.
 *
 * <p>Covers all feature scenarios from {@code scope-var-completion.feature}:
 * <ul>
 *   <li>ViewModel ID suggested at root position for all 6 scope annotation types</li>
 *   <li>ViewModel ID still suggested when a partial prefix has been typed</li>
 *   <li>Template loop variable from enclosing {@code <template var="...">}</li>
 *   <li>Loop variables from every enclosing {@code <template>} ancestor</li>
 *   <li>No template var when {@code <template>} has no {@code var} attribute</li>
 *   <li>User-defined {@code <apply>} passdown variable offered; ZK system attributes excluded</li>
 *   <li>No apply var when all attributes are ZK system attributes</li>
 *   <li>Attributes on the current {@code <apply>} tag not offered while editing its own value</li>
 * </ul>
 * </p>
 *
 * <p>All tests use {@code MockedStatic<ZulDomUtil>} to intercept the static helpers
 * {@code isZKFile}, {@code hasViewModel}, {@code findViewModelTag}, and
 * {@code extractViewModelId}.</p>
 *
 * <p>Standard "all guards pass" setup shared across tests:
 * <ul>
 *   <li>parameters.getOriginalFile() → XmlFile mock</li>
 *   <li>parameters.getPosition() → PsiElement whose getParent() → XmlAttributeValue</li>
 *   <li>position.getText() → {@code "@load(" + DUMMY_IDENTIFIER} unless stated otherwise</li>
 *   <li>attrValue.getParent() → XmlAttribute → getParent() → XmlTag (containingTag)</li>
 *   <li>result.withPrefixMatcher("") → prefixResult mock</li>
 *   <li>isZKFile=true, hasViewModel=true</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ZulScopeVarCompletionContentTest {

    private final ZulScopeVarCompletionContributor contributor =
            new ZulScopeVarCompletionContributor();

    // ─── helpers ──────────────────────────────────────────────────────────────

    private record TagChain(
            XmlFile psiFile,
            PsiElement position,
            XmlAttributeValue attrValue,
            XmlAttribute containingAttr,
            XmlTag containingTag
    ) {}

    /** Builds the standard mock chain with {@code "@load(" + DUMMY_IDENTIFIER} as the element text. */
    private TagChain buildTagChain() {
        return buildTagChain("@load(" + CompletionUtilCore.DUMMY_IDENTIFIER);
    }

    /** Builds the standard mock chain with a custom full element text (must include DUMMY_IDENTIFIER). */
    private TagChain buildTagChain(String fullElementText) {
        XmlFile           psiFile       = mock(XmlFile.class);
        PsiElement        position      = mock(PsiElement.class);
        XmlAttributeValue attrValue     = mock(XmlAttributeValue.class);
        XmlAttribute      containingAttr = mock(XmlAttribute.class);
        XmlTag            containingTag  = mock(XmlTag.class);

        when(position.getParent()).thenReturn(attrValue);
        when(position.getText()).thenReturn(fullElementText);
        when(attrValue.getParent()).thenReturn(containingAttr);
        when(containingAttr.getParent()).thenReturn(containingTag);

        return new TagChain(psiFile, position, attrValue, containingAttr, containingTag);
    }

    private CompletionParameters buildParams(TagChain chain) {
        CompletionParameters parameters = mock(CompletionParameters.class);
        when(parameters.getOriginalFile()).thenReturn(chain.psiFile());
        when(parameters.getPosition()).thenReturn(chain.position());
        return parameters;
    }

    /** Stubs the three ZulDomUtil static calls that every content test needs. */
    private void stubUtils(MockedStatic<ZulDomUtil> util, TagChain chain, XmlTag vmTag) {
        util.when(() -> ZulDomUtil.isZKFile(chain.psiFile())).thenReturn(true);
        util.when(() -> ZulDomUtil.hasViewModel(chain.position())).thenReturn(true);
        util.when(() -> ZulDomUtil.findViewModelTag(chain.position())).thenReturn(vmTag);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ViewModel ID suggestions
    // Feature: "Completion suggests the ViewModel ID at the root argument position"
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature Scenario Outline: all six scope-variable annotation types must suggest the VM id.
     * {@code @converter} and {@code @validator} are intentionally absent — they are covered
     * in {@link ZulScopeVarCompletionGuardTest}.
     */
    @ParameterizedTest(name = "vmId_isAdded_for_{0}")
    @ValueSource(strings = {"@load(", "@bind(", "@save(", "@init(", "@command(", "@global-command("})
    void vmId_isAdded_forAllScopeAnnotationTypes(String annotationPrefix) {
        String text = annotationPrefix + CompletionUtilCore.DUMMY_IDENTIFIER;
        TagChain             chain      = buildTagChain(text);
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        XmlTag vmTag = mock(XmlTag.class);
        when(vmTag.getAttributeValue(ZulDomUtil.VIEW_MODEL))
                .thenReturn("@id('vm') @init('com.example.MyViewModel')");
        when(chain.containingTag().getParent()).thenReturn(null);
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, vmTag);
            util.when(() -> ZulDomUtil.extractViewModelId(
                    "@id('vm') @init('com.example.MyViewModel')")).thenReturn("vm");

            contributor.fillCompletionVariants(parameters, result);
        }

        ArgumentCaptor<LookupElement> captor = ArgumentCaptor.forClass(LookupElement.class);
        verify(prefixResult, times(1)).addElement(captor.capture());
        assertEquals("vm", captor.getValue().getLookupString());
    }

    /**
     * Feature Scenario: "Completion still suggests the ViewModel ID when a partial prefix
     * has already been typed".
     * Typing "v" after the open paren sets prefix="v"; the VM id "vm" is still offered.
     */
    @Test
    void vmId_isSuggested_whenPartialPrefixAlreadyTyped() {
        // textBeforeCursor = "@load(v" — prefix extracted as "v"
        String text = "@load(v" + CompletionUtilCore.DUMMY_IDENTIFIER;
        TagChain             chain      = buildTagChain(text);
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        XmlTag vmTag = mock(XmlTag.class);
        when(vmTag.getAttributeValue(ZulDomUtil.VIEW_MODEL))
                .thenReturn("@id('vm') @init('com.example.MyViewModel')");
        when(chain.containingTag().getParent()).thenReturn(null);
        when(result.withPrefixMatcher("v")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, vmTag);
            util.when(() -> ZulDomUtil.extractViewModelId(
                    "@id('vm') @init('com.example.MyViewModel')")).thenReturn("vm");

            contributor.fillCompletionVariants(parameters, result);
        }

        ArgumentCaptor<LookupElement> captor = ArgumentCaptor.forClass(LookupElement.class);
        verify(prefixResult, times(1)).addElement(captor.capture());
        assertEquals("vm", captor.getValue().getLookupString());
    }

    @Test
    void vmId_notAdded_whenFindViewModelTagReturnsNull() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        when(chain.containingTag().getParent()).thenReturn(null);
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null /* vmTag = null */);

            contributor.fillCompletionVariants(parameters, result);
        }

        verify(prefixResult, never()).addElement(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Template var suggestions
    // Feature: "Completion suggests the loop variable from an enclosing <template var='...'> tag"
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void templateVar_isAdded_whenImmediateAncestorIsTemplateWithVar() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        XmlTag       templateAncestor = mock(XmlTag.class);
        XmlAttribute varAttr          = mock(XmlAttribute.class);
        when(chain.containingTag().getParent()).thenReturn(templateAncestor);
        when(templateAncestor.getParent()).thenReturn(null);
        when(templateAncestor.getLocalName()).thenReturn("template");
        when(templateAncestor.getAttribute("var")).thenReturn(varAttr);
        when(varAttr.getValue()).thenReturn("member");
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null);

            contributor.fillCompletionVariants(parameters, result);
        }

        ArgumentCaptor<LookupElement> captor = ArgumentCaptor.forClass(LookupElement.class);
        verify(prefixResult, times(1)).addElement(captor.capture());
        assertEquals("member", captor.getValue().getLookupString());
    }

    /**
     * Feature Scenario: "Completion suggests loop variables from every enclosing <template> ancestor".
     * Nested templates contribute independently; both vars are offered.
     */
    @Test
    void templateVar_bothAncestors_addedInEncounterOrder() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        XmlTag       innerTemplate = mock(XmlTag.class);
        XmlAttribute innerVar      = mock(XmlAttribute.class);
        XmlTag       outerTemplate = mock(XmlTag.class);
        XmlAttribute outerVar      = mock(XmlAttribute.class);

        when(chain.containingTag().getParent()).thenReturn(innerTemplate);
        when(innerTemplate.getParent()).thenReturn(outerTemplate);
        when(outerTemplate.getParent()).thenReturn(null);

        when(innerTemplate.getLocalName()).thenReturn("template");
        when(innerTemplate.getAttribute("var")).thenReturn(innerVar);
        when(innerVar.getValue()).thenReturn("row");

        when(outerTemplate.getLocalName()).thenReturn("template");
        when(outerTemplate.getAttribute("var")).thenReturn(outerVar);
        when(outerVar.getValue()).thenReturn("item");

        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null);

            contributor.fillCompletionVariants(parameters, result);
        }

        ArgumentCaptor<LookupElement> captor = ArgumentCaptor.forClass(LookupElement.class);
        verify(prefixResult, times(2)).addElement(captor.capture());
        List<LookupElement> elements = captor.getAllValues();
        assertEquals("row",  elements.get(0).getLookupString(), "inner template var first");
        assertEquals("item", elements.get(1).getLookupString(), "outer template var second");
    }

    /**
     * Feature Scenario: "Completion suggests the default variable 'each' when the
     * {@code <template>} tag has no var attribute".
     * <p>
     * In ZK Framework, a {@code <template>} without a {@code var} attribute exposes the
     * implicit loop variable {@code each}. The contributor must offer "each" in this case.
     */
    @Test
    void templateVar_defaultEach_suggestedWhenTemplateAncestorHasNoVarAttribute() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        XmlTag templateAncestor = mock(XmlTag.class);
        when(chain.containingTag().getParent()).thenReturn(templateAncestor);
        when(templateAncestor.getParent()).thenReturn(null);
        when(templateAncestor.getLocalName()).thenReturn("template");
        when(templateAncestor.getAttribute("var")).thenReturn(null); // no var attribute → use "each"
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null);

            contributor.fillCompletionVariants(parameters, result);
        }

        ArgumentCaptor<LookupElement> captor = ArgumentCaptor.forClass(LookupElement.class);
        verify(prefixResult, times(1)).addElement(captor.capture());
        assertEquals("each", captor.getValue().getLookupString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Apply passdown variable suggestions
    // Feature: "Completion suggests user-defined passdown variable from an enclosing <apply> tag"
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature Scenario: user-defined attr "ctx" is offered; ZK system attr "templateURI" is excluded.
     */
    @Test
    void applyVar_nonSystemAttr_isAdded_systemAttr_isExcluded() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        XmlTag       applyAncestor   = mock(XmlTag.class);
        XmlAttribute ctxAttr         = mock(XmlAttribute.class);
        XmlAttribute templateUriAttr = mock(XmlAttribute.class);

        when(chain.containingTag().getParent()).thenReturn(applyAncestor);
        when(applyAncestor.getParent()).thenReturn(null);
        when(applyAncestor.getLocalName()).thenReturn("apply");
        when(applyAncestor.getAttributes())
                .thenReturn(new XmlAttribute[]{ctxAttr, templateUriAttr});
        when(ctxAttr.getName()).thenReturn("ctx");
        when(templateUriAttr.getName()).thenReturn("templateURI");
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null);

            contributor.fillCompletionVariants(parameters, result);
        }

        ArgumentCaptor<LookupElement> captor = ArgumentCaptor.forClass(LookupElement.class);
        verify(prefixResult, times(1)).addElement(captor.capture());
        assertEquals("ctx", captor.getValue().getLookupString());
    }

    /**
     * Feature Scenario: "No apply variable suggestion when all <apply> attributes are ZK system attributes".
     */
    @Test
    void applyVar_noElementAdded_whenAllAttrsAreSystemAttrs() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        XmlTag       applyAncestor   = mock(XmlTag.class);
        XmlAttribute templateUriAttr = mock(XmlAttribute.class);
        XmlAttribute ifAttr          = mock(XmlAttribute.class);

        when(chain.containingTag().getParent()).thenReturn(applyAncestor);
        when(applyAncestor.getParent()).thenReturn(null);
        when(applyAncestor.getLocalName()).thenReturn("apply");
        when(applyAncestor.getAttributes())
                .thenReturn(new XmlAttribute[]{templateUriAttr, ifAttr});
        when(templateUriAttr.getName()).thenReturn("templateURI");
        when(ifAttr.getName()).thenReturn("if");
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null);

            contributor.fillCompletionVariants(parameters, result);
        }

        verify(prefixResult, never()).addElement(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Self-exclusion: current tag's own attributes must not be offered
    // Feature: "Attributes on the current <apply> tag are not offered while editing its own value"
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature Scenario: the developer is editing the value of "ctx" on the current {@code <apply>}
     * tag. "ctx" must NOT appear in completions because the ancestor walk begins at
     * {@code containingTag.getParent()}, skipping the containing tag itself.
     *
     * <p>This test verifies both outcomes:
     * <ul>
     *   <li>{@code containingTag.getAttributes()} is never called (walk skips self)</li>
     *   <li>no lookup element is added to the result</li>
     * </ul>
     * </p>
     */
    @Test
    void applyVar_onCurrentContainingTag_isNotSuggested_whileEditingOwnValue() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        // Document that containingTag IS an <apply> with a user-defined "ctx" attribute.
        // These stubs are lenient because the ancestor walk never reaches containingTag itself —
        // the walk starts at containingTag.getParent(), so these methods are never called.
        XmlAttribute ctxAttr = mock(XmlAttribute.class);
        lenient().when(ctxAttr.getName()).thenReturn("ctx");
        lenient().when(chain.containingTag().getLocalName()).thenReturn("apply");
        lenient().when(chain.containingTag().getAttributes()).thenReturn(new XmlAttribute[]{ctxAttr});
        // Walk starts at parent → null means no ancestors, so the loop body never executes
        when(chain.containingTag().getParent()).thenReturn(null);
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null);

            contributor.fillCompletionVariants(parameters, result);
        }

        // The ancestor walk must never read the containing tag's own attribute list
        verify(chain.containingTag(), never()).getAttributes();
        // "ctx" must not appear in the completion list
        verify(prefixResult, never()).addElement(any());
    }

    /**
     * Baseline test: the ancestor walk starts at {@code containingTag.getParent()},
     * so when that parent is null no elements are ever added regardless of the
     * containing tag's own structure.
     */
    @Test
    void ancestorWalk_doesNotVisitContainingTagItself() {
        TagChain             chain      = buildTagChain();
        CompletionParameters parameters = buildParams(chain);
        CompletionResultSet  result     = mock(CompletionResultSet.class);
        CompletionResultSet  prefixResult = mock(CompletionResultSet.class);

        when(chain.containingTag().getParent()).thenReturn(null);
        when(result.withPrefixMatcher("")).thenReturn(prefixResult);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            stubUtils(util, chain, null);

            contributor.fillCompletionVariants(parameters, result);
        }

        verify(prefixResult, never()).addElement(any());
    }
}
