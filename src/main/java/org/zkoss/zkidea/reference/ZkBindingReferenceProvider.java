package org.zkoss.zkidea.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Provides PsiReferences for ViewModel binding expressions in ZUL files.
 * <p>
 * Handles:
 * <ul>
 *   <li>VM property chains: {@code vm.crew.name} inside any recognized annotation</li>
 *   <li>Scope variable chains: {@code wrapper.dto}, {@code item.name} (template, apply, forEachVar)</li>
 *   <li>Command string literals: {@code 'saveItem'} in {@code @command('saveItem')}</li>
 *   <li>Before/after command params: {@code before='validate'} in {@code @save(...)}</li>
 * </ul>
 */
public class ZkBindingReferenceProvider extends PsiReferenceProvider {
    private static final Logger LOG = Logger.getInstance(ZkBindingReferenceProvider.class);

    /**
     * Matches recognized ZK binding annotations and captures (1) the annotation name
     * and (2) the annotation body content.
     * Built from {@link ZulDomUtil#BINDING_ANNOTATIONS} so the list stays in sync.
     */
    private static final Pattern BINDING_ANNOTATION_PATTERN =
            Pattern.compile("@(" + ZulDomUtil.BINDING_ANNOTATIONS + ")\\s*\\(([^)]*)\\)");

    /** Matches the string literal command name inside {@code @command('...')} etc. */
    private static final Pattern COMMAND_STRING_PATTERN =
            Pattern.compile("@(?:command|global-command)\\s*\\(\\s*['\"]([^'\"]+)['\"]");

    /**
     * Matches {@code before='commandName'} or {@code after='commandName'} named
     * parameters that appear inside any binding annotation body.
     */
    private static final Pattern BEFORE_AFTER_PATTERN =
            Pattern.compile("\\b(?:before|after)\\s*=\\s*['\"]([^'\"]+)['\"]");

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

        // Offset from element start to the value content (skip opening quote)
        int valueOffset = attrValue.getValueTextRange().getStartOffset()
                - attrValue.getTextRange().getStartOffset();

        List<PsiReference> references = new ArrayList<>();

        // --- Pass 1: identifier-chain references (VM properties + scope variables) ---
        Matcher annotMatcher = BINDING_ANNOTATION_PATTERN.matcher(text);
        while (annotMatcher.find()) {
            String annotationName = annotMatcher.group(1);
            String innerContent = annotMatcher.group(2);
            int innerOffset = annotMatcher.start(2);

            // Skip quoted-string arguments (file paths) — handled by ZkTemplateUriReferenceProvider.
            // Avoids extracting identifier tokens from inside path strings like '/mentor/crew/foo.zul'.
            String trimmedInner = innerContent.trim();
            if (trimmedInner.startsWith("'") || trimmedInner.startsWith("\"")) continue;

            Matcher chainMatcher = IDENTIFIER_CHAIN_PATTERN.matcher(innerContent);
            while (chainMatcher.find()) {
                String chain = chainMatcher.group();
                int chainStart = innerOffset + chainMatcher.start();
                processChain(attrValue, chain, valueOffset + chainStart,
                        vmId, vmClass, references, annotationName);
            }

            // before/after command name references within this annotation body
            Matcher beforeAfterMatcher = BEFORE_AFTER_PATTERN.matcher(innerContent);
            while (beforeAfterMatcher.find()) {
                String cmdName = beforeAfterMatcher.group(1);
                int cmdStart = valueOffset + innerOffset + beforeAfterMatcher.start(1);
                int cmdEnd = valueOffset + innerOffset + beforeAfterMatcher.end(1);
                LOG.debug("ZkBindingReferenceProvider: before/after command '" + cmdName + "'");
                if (vmClass != null) {
                    references.add(new ZkCommandReference(
                            attrValue, new TextRange(cmdStart, cmdEnd), vmClass, cmdName));
                }
            }
        }

        // --- Pass 2: @command('literal') string references ---
        Matcher cmdMatcher = COMMAND_STRING_PATTERN.matcher(text);
        while (cmdMatcher.find()) {
            String commandName = cmdMatcher.group(1);
            int cmdStart = valueOffset + cmdMatcher.start(1);
            int cmdEnd = valueOffset + cmdMatcher.end(1);
            LOG.debug("ZkBindingReferenceProvider: command string '" + commandName + "'");
            if (vmClass != null) {
                references.add(new ZkCommandReference(
                        attrValue, new TextRange(cmdStart, cmdEnd), vmClass, commandName));
            }
        }

        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    private void processChain(XmlAttributeValue element, String chain, int offsetInElement,
                              String vmId, PsiClass vmClass, List<PsiReference> references,
                              String annotationName) {
        String[] segments = chain.split("\\.");
        if (segments.length == 0) return;

        TextRange idRange = new TextRange(offsetInElement, offsetInElement + segments[0].length());

        // Determine start class (VM or scope variable)
        PsiClass startClass;
        if (segments[0].equals(vmId)) {
            if (vmClass != null) {
                references.add(new ViewModelIdReference(element, idRange, vmClass));
            }
            startClass = vmClass;
        } else {
            XmlAttribute scopeDecl = ZulDomUtil.findScopeVariableDeclaration(element, segments[0]);
            if (scopeDecl == null) {
                LOG.debug("processChain: '" + segments[0] + "' is not vmId and no scope declaration found");
                return;
            }
            references.add(new ZulScopeVariableReference(element, idRange, scopeDecl));
            startClass = ZulDomUtil.resolveScopeVariableType(scopeDecl, vmId, vmClass, element);
        }

        // isCommandContext: inside @command or @global-command, completion offers @Command names
        boolean isCommandContext = "command".equals(annotationName)
                || "global-command".equals(annotationName);

        // Walk property segments
        PsiClass currentClass = startClass;
        int currentOffset = offsetInElement + segments[0].length() + 1; // +1 for dot
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            TextRange propRange = new TextRange(currentOffset, currentOffset + segment.length());
            references.add(new ViewModelPropertyReference(
                    element, propRange, currentClass, segment, isCommandContext));

            // Resolve type for the next segment
            currentClass = resolvePropertyType(currentClass, segment, element);
            currentOffset += segment.length() + 1; // +1 for dot
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
