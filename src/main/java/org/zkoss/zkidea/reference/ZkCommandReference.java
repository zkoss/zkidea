package org.zkoss.zkidea.reference;

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference from a ZK command name string literal (e.g., {@code 'saveItem'} in
 * {@code @command('saveItem')}) to the {@code @Command}-annotated method in the ViewModel.
 * <p>
 * Also provides completion variants listing all {@code @Command}-annotated method names.
 */
public class ZkCommandReference extends PsiReferenceBase<XmlAttributeValue> {
    private static final Logger LOG = Logger.getInstance(ZkCommandReference.class);

    private static final String COMMAND_ANNOTATION = "org.zkoss.bind.annotation.Command";
    private static final String GLOBAL_COMMAND_ANNOTATION = "org.zkoss.bind.annotation.GlobalCommand";

    private final PsiClass vmClass;
    private final String commandName;

    public ZkCommandReference(XmlAttributeValue element, TextRange rangeInElement,
                              PsiClass vmClass, String commandName) {
        super(element, rangeInElement);
        this.vmClass = vmClass;
        this.commandName = commandName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        if (vmClass == null) {
            LOG.debug("ZkCommandReference.resolve: vmClass null for commandName=" + commandName);
            return null;
        }
        for (PsiMethod method : vmClass.getAllMethods()) {
            if (commandName.equals(getCommandName(method))) {
                LOG.debug("ZkCommandReference.resolve: '" + commandName + "' → " + method.getName());
                return method;
            }
        }
        LOG.debug("ZkCommandReference.resolve: no @Command method found for '" + commandName + "'");
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        if (vmClass == null) return EMPTY_ARRAY;
        List<LookupElement> variants = buildCommandLookupElements(vmClass);
        LOG.debug("ZkCommandReference.getVariants: " + variants.size()
                + " command variants for " + vmClass.getName());
        return variants.toArray();
    }

    /**
     * Builds lookup elements for all {@code @Command}/{@code @GlobalCommand}-annotated
     * methods on the given class. Shared by {@link ZkCommandReference#getVariants()} and
     * {@link ViewModelPropertyReference#getCommandVariants()}.
     */
    public static List<LookupElement> buildCommandLookupElements(PsiClass psiClass) {
        List<LookupElement> variants = new ArrayList<>();
        for (PsiMethod method : psiClass.getAllMethods()) {
            String cmdName = getCommandName(method);
            if (cmdName != null) {
                String containingClassName = method.getContainingClass() != null
                        ? method.getContainingClass().getName() : "";
                variants.add(LookupElementBuilder.create(cmdName)
                        .withIcon(AllIcons.Nodes.Method)
                        .withTypeText("@Command")
                        .withTailText("  (" + containingClassName + ")", true)
                        .withInsertHandler((ctx, item) ->
                                ctx.getDocument().replaceString(
                                        ctx.getStartOffset(), ctx.getTailOffset(),
                                        "'" + item.getLookupString() + "'")));
            }
        }
        return variants;
    }

    /**
     * Returns the effective command name for a method if it is annotated with
     * {@code @Command} or {@code @GlobalCommand}; otherwise returns {@code null}.
     * <p>
     * Uses the annotation's explicit {@code value} attribute if present, otherwise
     * falls back to the method name.
     */
    @Nullable
    static String getCommandName(PsiMethod method) {
        for (PsiAnnotation annotation : method.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (COMMAND_ANNOTATION.equals(qualifiedName)
                    || GLOBAL_COMMAND_ANNOTATION.equals(qualifiedName)) {
                PsiAnnotationMemberValue valueAttr = annotation.findDeclaredAttributeValue("value");
                if (valueAttr != null) {
                    String rawValue = valueAttr.getText();
                    // Strip surrounding quotes from string literal
                    if (rawValue.length() >= 2
                            && (rawValue.startsWith("\"") || rawValue.startsWith("'"))) {
                        rawValue = rawValue.substring(1, rawValue.length() - 1);
                    }
                    if (!rawValue.isEmpty() && !"null".equals(rawValue)) {
                        return rawValue;
                    }
                }
                // No explicit value — use method name
                return method.getName();
            }
        }
        return null;
    }
}
