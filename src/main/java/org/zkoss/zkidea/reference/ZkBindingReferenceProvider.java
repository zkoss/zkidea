package org.zkoss.zkidea.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Provides PsiReferences for ViewModel binding expressions in ZUL files.
 * Parses expressions like vm.crew.name inside @load(), @bind(), @save(), etc.
 * and creates references for the identifier and each property segment.
 */
public class ZkBindingReferenceProvider extends PsiReferenceProvider {
    private static final Pattern BINDING_ANNOTATION_PATTERN =
            Pattern.compile("@(?:load|bind|save|init|command|global-command)\\s*\\(([^)]*)\\)");
    private static final Pattern IDENTIFIER_CHAIN_PATTERN =
            Pattern.compile("[a-zA-Z_]\\w*(?:\\.[a-zA-Z_]\\w*)*");

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                            @NotNull ProcessingContext context) {
        if (!(element instanceof XmlAttributeValue)) return PsiReference.EMPTY_ARRAY;

        XmlAttributeValue attrValue = (XmlAttributeValue) element;
        if (!ZulDomUtil.isZKFile(element.getContainingFile())) return PsiReference.EMPTY_ARRAY;

        XmlTag viewModelTag = ZulDomUtil.findViewModelTag(element);
        if (viewModelTag == null) return PsiReference.EMPTY_ARRAY;

        String vmAttrValue = viewModelTag.getAttributeValue(ZulDomUtil.VIEW_MODEL);
        if (vmAttrValue == null) return PsiReference.EMPTY_ARRAY;

        String vmId = ZulDomUtil.extractViewModelId(vmAttrValue);
        if (vmId == null) return PsiReference.EMPTY_ARRAY;

        PsiClass vmClass = ZulDomUtil.resolveViewModelClass(element.getProject(), vmAttrValue);

        String text = attrValue.getValue();
        if (text == null || text.isEmpty()) return PsiReference.EMPTY_ARRAY;

        // offset from element start to the value content (skip opening quote)
        int valueOffset = attrValue.getValueTextRange().getStartOffset() - attrValue.getTextRange().getStartOffset();

        List<PsiReference> references = new ArrayList<>();

        Matcher annotMatcher = BINDING_ANNOTATION_PATTERN.matcher(text);
        while (annotMatcher.find()) {
            String innerContent = annotMatcher.group(1);
            int innerOffset = annotMatcher.start(1);

            Matcher chainMatcher = IDENTIFIER_CHAIN_PATTERN.matcher(innerContent);
            while (chainMatcher.find()) {
                String chain = chainMatcher.group();
                int chainStart = innerOffset + chainMatcher.start();

                processChain(attrValue, chain, valueOffset + chainStart, vmId, vmClass, references);
            }
        }

        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    private void processChain(XmlAttributeValue element, String chain, int offsetInElement,
                              String vmId, PsiClass vmClass, List<PsiReference> references) {
        String[] segments = chain.split("\\.");
        if (segments.length == 0) return;

        // First segment must match the @id value
        if (!segments[0].equals(vmId)) return;

        // Reference for the vm identifier
        TextRange idRange = new TextRange(offsetInElement, offsetInElement + segments[0].length());
        if (vmClass != null) {
            references.add(new ViewModelIdReference(element, idRange, vmClass));
        }

        // Process property segments
        PsiClass currentClass = vmClass;
        int currentOffset = offsetInElement + segments[0].length() + 1; // +1 for the dot
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            TextRange propRange = new TextRange(currentOffset, currentOffset + segment.length());

            references.add(new ViewModelPropertyReference(element, propRange, currentClass, segment));

            // Resolve type for next segment
            currentClass = resolvePropertyType(currentClass, segment, element);
            currentOffset += segment.length() + 1; // +1 for the dot
        }
    }

    private PsiClass resolvePropertyType(PsiClass ownerClass, String property, PsiElement context) {
        if (ownerClass == null) return null;
        PsiMethod getter = ViewModelPropertyReference.findGetter(ownerClass, property);
        if (getter == null) return null;
        PsiType returnType = getter.getReturnType();
        if (returnType == null) return null;
        // Resolve the deep component type (unwrap generics, arrays)
        PsiType deepType = returnType.getDeepComponentType();
        String canonicalText = deepType.getCanonicalText();
        // Strip generic parameters if present
        int genericIndex = canonicalText.indexOf('<');
        if (genericIndex > 0) {
            canonicalText = canonicalText.substring(0, genericIndex);
        }
        return JavaPsiFacade.getInstance(context.getProject())
                .findClass(canonicalText, GlobalSearchScope.allScope(context.getProject()));
    }
}
