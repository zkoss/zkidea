package org.zkoss.zkidea.completion;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Provides root scope-variable name suggestions at the start of a ZK binding annotation argument.
 * <p>
 * Triggers when the cursor is inside an open binding annotation (e.g., {@code @load(...)}) and
 * no dot has been typed yet (root position). Suggests the ViewModel id, template variables
 * (from enclosing {@code <template var="...">}), and apply passdown variables (from enclosing
 * {@code <apply>} tags above the containing tag).
 * <p>
 * Example: typing {@code wra} inside {@code @load(...)} with a {@code <template var="wrapper">}
 * ancestor shows {@code wrapper} in the completion popup.
 */
public class ZulScopeVarCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(ZulScopeVarCompletionContributor.class);

    /**
     * Matches the text before the cursor when the cursor is at root position inside a binding
     * annotation (no dot before cursor, annotation is open).
     */
    private static final Pattern BINDING_ROOT_PATTERN = Pattern.compile(
            "@(?:load|bind|save|init|command|global-command)\\s*\\(\\s*[^.)]*$");

    /**
     * System attributes on {@code <apply>} that are not scope variables.
     */
    private static final Set<String> SYSTEM_ATTRS = new HashSet<>(Arrays.asList(
            "templateURI", "template", "if", "unless", "forEach", "forEachBegin", "forEachEnd",
            "forEachStep", "forEachStatus", "forEachIndex"
    ));

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        PsiFile psiFile = parameters.getOriginalFile();
        if (!(psiFile instanceof XmlFile)) return;
        if (!ZulDomUtil.isZKFile(psiFile)) return;

        PsiElement position = parameters.getPosition();
        PsiElement parent = position.getParent();
        if (!(parent instanceof XmlAttributeValue)) return;

        if (!ZulDomUtil.hasViewModel(position)) return;

        String text = position.getText();
        int dummyIndex = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER);
        if (dummyIndex < 0) return;

        String textBeforeCursor = text.substring(0, dummyIndex);

        // Check trigger condition: inside a binding annotation at root position (no dot)
        Matcher triggerMatcher = BINDING_ROOT_PATTERN.matcher(textBeforeCursor);
        if (!triggerMatcher.find()) return;

        // Extract the prefix already typed after the open parenthesis
        int lastOpenParen = textBeforeCursor.lastIndexOf('(');
        String prefix = lastOpenParen >= 0
                ? textBeforeCursor.substring(lastOpenParen + 1).stripLeading()
                : "";

        LOG.debug("ZulScopeVarCompletionContributor triggered, prefix='" + prefix + "'");

        CompletionResultSet prefixResult = result.withPrefixMatcher(prefix);

        // Offer the ViewModel id
        XmlTag viewModelTag = ZulDomUtil.findViewModelTag(position);
        if (viewModelTag != null) {
            String vmAttrValue = viewModelTag.getAttributeValue(ZulDomUtil.VIEW_MODEL);
            String vmId = ZulDomUtil.extractViewModelId(vmAttrValue);
            if (vmId != null) {
                prefixResult.addElement(LookupElementBuilder.create(vmId)
                        .withIcon(AllIcons.Nodes.Class)
                        .withTypeText("ViewModel"));
                LOG.debug("ZulScopeVarCompletionContributor: added VM id '" + vmId + "'");
            }
        }

        // The tag that directly contains the attribute being typed
        XmlAttributeValue attrValue = (XmlAttributeValue) parent;
        XmlAttribute containingAttr = (XmlAttribute) attrValue.getParent();
        XmlTag containingTag = containingAttr.getParent();

        // Walk ancestors starting from the PARENT of the containing tag (skip self to avoid
        // offering attributes whose values are currently being defined)
        PsiElement current = containingTag.getParent();
        while (current != null) {
            if (current instanceof XmlTag) {
                XmlTag tag = (XmlTag) current;
                String tagName = tag.getLocalName();

                if ("template".equals(tagName)) {
                    XmlAttribute varAttr = tag.getAttribute("var");
                    if (varAttr != null && varAttr.getValue() != null) {
                        String varName = varAttr.getValue();
                        prefixResult.addElement(LookupElementBuilder.create(varName)
                                .withIcon(AllIcons.Nodes.Variable)
                                .withTypeText("template var"));
                        LOG.debug("ZulScopeVarCompletionContributor: added template var '" + varName + "'");
                    }
                } else if ("apply".equals(tagName)) {
                    for (XmlAttribute attr : tag.getAttributes()) {
                        String attrName = attr.getName();
                        if (!SYSTEM_ATTRS.contains(attrName)) {
                            prefixResult.addElement(LookupElementBuilder.create(attrName)
                                    .withIcon(AllIcons.Nodes.Variable)
                                    .withTypeText("apply var"));
                            LOG.debug("ZulScopeVarCompletionContributor: added apply var '" + attrName + "'");
                        }
                    }
                }
            }
            current = current.getParent();
        }
    }
}