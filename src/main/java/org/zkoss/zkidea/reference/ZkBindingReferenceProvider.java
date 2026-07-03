package org.zkoss.zkidea.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zkoss.zkidea.dom.ZulDomUtil;

/**
 * Provides PsiReferences for ViewModel binding expressions in ZUL files.
 * <p>
 * Handles:
 * <ul>
 *   <li>VM property chains: {@code vm.crew.name} inside any recognized annotation</li>
 *   <li>Method call chains: {@code vm.getItems().isEmpty()} with balanced parentheses</li>
 *   <li>Command string literals: {@code 'saveItem'} in {@code @command('saveItem')}</li>
 *   <li>Before/after command params: {@code before='validate'} in {@code @save(...)}</li>
 * </ul>
 */
public class ZkBindingReferenceProvider extends PsiReferenceProvider {
    private static final Logger LOG = Logger.getInstance(ZkBindingReferenceProvider.class);

    /**
     * Matches the start of a recognized ZK binding annotation up to and including the
     * opening parenthesis. Captures (1) the annotation name.
     * The body is then extracted using balanced-parenthesis scanning so that method
     * calls like {@code vm.hasPermission('X')} inside the annotation don't truncate it.
     */
    private static final Pattern ANNOTATION_START_PATTERN =
            Pattern.compile("@(" + ZulDomUtil.BINDING_ANNOTATIONS + ")\\s*\\(");

    /** Matches the string literal command name inside {@code @command('...')} etc. */
    static final Pattern COMMAND_STRING_PATTERN =
            Pattern.compile("@(?:command|global-command)\\s*\\(\\s*['\"]([^'\"]+)['\"]");

    /**
     * Matches {@code before='commandName'} or {@code after='commandName'} named
     * parameters that appear inside any binding annotation body.
     */
    static final Pattern BEFORE_AFTER_PATTERN =
            Pattern.compile("\\b(?:before|after)\\s*=\\s*['\"]([^'\"]+)['\"]");

    // -------------------------------------------------------------------------
    // Annotation body and chain extraction (balanced-paren aware)
    // -------------------------------------------------------------------------

    /** A parsed annotation occurrence within the attribute value text. */
    static class AnnotationMatch {
        final String name;
        final String body;
        final int bodyStartOffset; // offset of body start within source text

        AnnotationMatch(String name, String body, int bodyStartOffset) {
            this.name = name;
            this.body = body;
            this.bodyStartOffset = bodyStartOffset;
        }
    }

    /** A single segment within a dotted chain (property or method call). */
    static class ChainSegment {
        final String name;
        final boolean isMethodCall;
        final int nameStartInBody; // offset of the identifier start relative to the body
        final int nameLength;

        ChainSegment(String name, boolean isMethodCall, int nameStartInBody, int nameLength) {
            this.name = name;
            this.isMethodCall = isMethodCall;
            this.nameStartInBody = nameStartInBody;
            this.nameLength = nameLength;
        }
    }

    /**
     * Extracts annotation occurrences from the attribute value text, using balanced-
     * parenthesis scanning so that nested {@code ()} inside the body don't truncate it.
     */
    static List<AnnotationMatch> extractAnnotations(String text) {
        List<AnnotationMatch> results = new ArrayList<>();
        Matcher m = ANNOTATION_START_PATTERN.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            int openParen = m.end() - 1; // position of '('
            int bodyStart = m.end();      // position right after '('
            int closeParen = findMatchingParen(text, openParen);
            // When no matching ')' exists (e.g. user is still typing), use the rest of
            // the text as the body so that completion still works on incomplete expressions.
            int bodyEnd = (closeParen < 0) ? text.length() : closeParen;
            String body = text.substring(bodyStart, bodyEnd);
            results.add(new AnnotationMatch(name, body, bodyStart));
        }
        return results;
    }

    /**
     * Finds the closing parenthesis that matches the opening one at {@code openPos},
     * skipping over string literals (single- and double-quoted).
     *
     * @return index of the matching {@code )}, or {@code -1} if not found
     */
    static int findMatchingParen(String text, int openPos) {
        int depth = 1;
        for (int i = openPos + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                int end = text.indexOf(c, i + 1);
                if (end < 0) return -1;
                i = end;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Extracts all dotted identifier chains from an annotation body, including
     * chains that contain method-call segments with parenthesized arguments.
     * <p>
     * For example, from {@code "vm.getItems().isEmpty()"} this produces a single
     * chain: {@code [vm, getItems(), isEmpty()]}.  String literals inside the body
     * are skipped so that path arguments are not mistakenly extracted.
     */
    static List<List<ChainSegment>> extractChains(String body) {
        List<List<ChainSegment>> chains = new ArrayList<>();
        int i = 0;
        int len = body.length();

        while (i < len) {
            char c = body.charAt(i);
            // Skip string literals
            if (c == '\'' || c == '"') {
                int end = body.indexOf(c, i + 1);
                i = (end < 0) ? len : end + 1;
                continue;
            }
            // Start of an identifier chain?
            if (isIdentStart(c)) {
                List<ChainSegment> chain = new ArrayList<>();
                while (i < len) {
                    // Read identifier name
                    int nameStart = i;
                    while (i < len && isIdentPart(body.charAt(i))) i++;
                    String name = body.substring(nameStart, i);

                    // Check for parenthesized arguments → method call
                    boolean isMethodCall = false;
                    if (i < len && body.charAt(i) == '(') {
                        int close = findMatchingParen(body, i);
                        if (close >= 0) {
                            isMethodCall = true;
                            i = close + 1;
                        }
                    }

                    chain.add(new ChainSegment(name, isMethodCall, nameStart, name.length()));

                    // Dot continuation?
                    if (i < len && body.charAt(i) == '.') {
                        i++; // skip dot
                        if (i < len && isIdentStart(body.charAt(i))) {
                            continue; // next segment
                        }
                        break; // trailing dot, end chain
                    }
                    break; // no dot, end chain
                }
                if (!chain.isEmpty()) {
                    chains.add(chain);
                }
            } else {
                i++;
            }
        }
        return chains;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // -------------------------------------------------------------------------
    // Binding context
    // -------------------------------------------------------------------------

    private static final class RequiredViewModelContext {
        final XmlAttributeValue attrValue;
        final String vmId;
        final PsiClass vmClass;
        final String text;
        final int valueOffset;

        RequiredViewModelContext(XmlAttributeValue attrValue, String vmId,
                                 PsiClass vmClass, String text, int valueOffset) {
            this.attrValue = attrValue;
            this.vmId = vmId;
            this.vmClass = vmClass;
            this.text = text;
            this.valueOffset = valueOffset;
        }
    }

    /**
     * Checks that {@code element} is an {@link XmlAttributeValue} inside a ZK file that
     * has a resolvable ViewModel with a non-empty binding expression.
     *
     * @return a fully-populated {@link RequiredViewModelContext}, or {@code null} if any
     *         precondition is not met and reference resolution should be skipped
     */
    @Nullable
    private static RequiredViewModelContext validate(PsiElement element) {
        if (!(element instanceof XmlAttributeValue)) return null;
        XmlAttributeValue attrValue = (XmlAttributeValue) element;
        if (!ZulDomUtil.isZKFile(element.getContainingFile())) return null;
        XmlTag viewModelTag = ZulDomUtil.findViewModelTag(element);
        if (viewModelTag == null) return null;
        String vmAttrValue = viewModelTag.getAttributeValue(ZulDomUtil.VIEW_MODEL);
        if (vmAttrValue == null) return null;
        String vmId = ZulDomUtil.extractViewModelId(vmAttrValue);
        if (vmId == null) return null;
        PsiClass vmClass = ZulDomUtil.resolveViewModelClass(element.getProject(), vmAttrValue);
        if (vmClass == null) return null;
        String text = attrValue.getValue();
        if (text == null || text.isEmpty()) return null;
        int valueOffset = attrValue.getValueTextRange().getStartOffset()
                - attrValue.getTextRange().getStartOffset();
        return new RequiredViewModelContext(attrValue, vmId, vmClass, text, valueOffset);
    }

    // -------------------------------------------------------------------------
    // Reference provider entry point
    // -------------------------------------------------------------------------

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                           @NotNull ProcessingContext context) {
        RequiredViewModelContext ctx = validate(element);
        if (ctx == null) return PsiReference.EMPTY_ARRAY;

        List<PsiReference> references = new ArrayList<>();
        collectViewModelPropertyReferences(ctx, references);
        collectBeforeAfterReferences(ctx, references);
        collectCommandLiteralReferences(ctx, references);
        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    // -------------------------------------------------------------------------
    // Chain processing and type resolution
    // -------------------------------------------------------------------------

    /** Collects VM property and scope-variable chain references from all annotation bodies
     *  (e.g. {@code vm.order.item} inside {@code @load}, {@code @bind}, {@code @save}, etc.). */
    private void collectViewModelPropertyReferences(RequiredViewModelContext ctx,
                                                    List<PsiReference> references) {
        for (AnnotationMatch annot : extractAnnotations(ctx.text)) {
            // Skip quoted-string arguments (file paths) — handled by ZkTemplateUriReferenceProvider.
            String trimmedBody = annot.body.trim();
            if (trimmedBody.startsWith("'") || trimmedBody.startsWith("\"")) continue;

            int bodyOffsetInElement = ctx.valueOffset + annot.bodyStartOffset;
            for (List<ChainSegment> chain : extractChains(annot.body)) {
                processChain(ctx.attrValue, chain, bodyOffsetInElement,
                        ctx.vmId, ctx.vmClass, references, annot.name);
            }
        }
    }

    /** Collects references for {@code before='cmd'} / {@code after='cmd'} parameters,
     *  which only appear inside command bindings such as {@code @save(..., before='validate')}. */
    private void collectBeforeAfterReferences(RequiredViewModelContext ctx,
                                              List<PsiReference> references) {
        for (AnnotationMatch annot : extractAnnotations(ctx.text)) {
            int bodyOffsetInElement = ctx.valueOffset + annot.bodyStartOffset;
            Matcher m = BEFORE_AFTER_PATTERN.matcher(annot.body);
            while (m.find()) {
                String cmdName = m.group(1);
                int cmdStart = bodyOffsetInElement + m.start(1);
                int cmdEnd = bodyOffsetInElement + m.end(1);
                LOG.debug("ZkBindingReferenceProvider: before/after command '" + cmdName + "'");
                references.add(new ZkCommandReference(
                        ctx.attrValue, new TextRange(cmdStart, cmdEnd), ctx.vmClass, cmdName));
            }
        }
    }


    /** Collects references for the command name literal inside {@code @command('name')}
     *  and {@code @global-command('name')}. */
    private void collectCommandLiteralReferences(RequiredViewModelContext ctx,
                                                 List<PsiReference> references) {
        Matcher m = COMMAND_STRING_PATTERN.matcher(ctx.text);
        while (m.find()) {
            String commandName = m.group(1);
            int cmdStart = ctx.valueOffset + m.start(1);
            int cmdEnd = ctx.valueOffset + m.end(1);
            LOG.debug("ZkBindingReferenceProvider: command string '" + commandName + "'");
            references.add(new ZkCommandReference(
                    ctx.attrValue, new TextRange(cmdStart, cmdEnd), ctx.vmClass, commandName));
        }
    }

    private void processChain(XmlAttributeValue element, List<ChainSegment> segments,
                              int bodyOffsetInElement, String vmId, PsiClass vmClass,
                              List<PsiReference> references, String annotationName) {
        if (segments.isEmpty()) return;

        // isCommandContext: inside @command or @global-command, completion offers @Command names
        boolean isCommandContext = "command".equals(annotationName)
                || "global-command".equals(annotationName);

        ChainSegment first = segments.get(0);
        int firstStart = bodyOffsetInElement + first.nameStartInBody;
        TextRange idRange = new TextRange(firstStart, firstStart + first.nameLength);

        // @command/@global-command bodies are bare command names, never prefixed with vmId.
        // When IntelliJ injects a dummy identifier for completion, the chain is a single
        // segment that doesn't match vmId — create a command-context reference directly so
        // that getVariants() can return @Command method names.
        if (isCommandContext && !first.name.equals(vmId)) {
            references.add(new ViewModelPropertyReference(
                    element, idRange, vmClass, first.name, true));
            return;
        }

        // Only process chains that start with the ViewModel ID
        if (!first.name.equals(vmId)) {
            LOG.debug("processChain: '" + first.name + "' is not vmId, skipping");
            return;
        }
        references.add(new ViewModelIdReference(element, idRange, vmClass));

        // Walk property/method segments — text ranges cover only the identifier name,
        // excluding any parenthesized arguments so only the method name is clickable.
        PsiClass currentClass = vmClass;
        PsiSubstitutor currentSubst = PsiSubstitutor.EMPTY;
        for (int i = 1; i < segments.size(); i++) {
            ChainSegment seg = segments.get(i);
            int segStart = bodyOffsetInElement + seg.nameStartInBody;
            TextRange propRange = new TextRange(segStart, segStart + seg.nameLength);
            references.add(new ViewModelPropertyReference(
                    element, propRange, currentClass, seg.name, isCommandContext));

            // Resolve the owner class for the next segment, carrying the generic type
            // arguments so that properties reached through an inherited generic getter
            // (e.g. T getModel() declared in a generic base) keep resolving.
            PsiMethod method = currentClass != null
                    ? ZulDomUtil.findGetterOrMethod(currentClass, seg.name) : null;
            if (method == null) {
                LOG.debug("processChain: no getter or method for '" + seg.name + "'");
                currentClass = null;
                currentSubst = PsiSubstitutor.EMPTY;
                continue;
            }
            PsiType nextType = ZulDomUtil.substituteReturnType(currentClass, currentSubst, method);
            currentSubst = ZulDomUtil.substitutorOf(nextType);
            currentClass = ZulDomUtil.resolveTypeToClass(nextType, element);
        }
    }
}
