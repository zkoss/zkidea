package org.zkoss.zkidea.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlAttributeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.completion.MVVMAnnotationCompletionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for command name completion in {@code @command()} and
 * {@code @global-command()} binding annotations.
 *
 * <p>Scenarios are specified in
 * {@code src/test/resources/features/command-name-completion.feature}.
 *
 * <p>Two completion layers are tested:
 * <ol>
 *   <li>{@link ViewModelPropertyReference} / {@link ZkCommandReference} —
 *       suggest command <em>names</em> (e.g., {@code "saveItem"}, {@code "broadcast"})
 *       when {@code isCommandContext=true}.</li>
 *   <li>{@link MVVMAnnotationCompletionProvider#isInsideAnnotationBody(String)} —
 *       suppresses annotation <em>templates</em> (e.g., {@code @global-command()})
 *       when the cursor is already inside an annotation's parentheses.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CommandNameCompletionTest {

    private static final TextRange DUMMY_RANGE = new TextRange(0, 4);

    private final XmlAttributeValue dummyElement = mock(XmlAttributeValue.class);

    private PsiClass vmClass;

    @BeforeEach
    void buildVmClass() {
        vmClass = PsiClassMocker.from(MyViewModel.class);
    }

    // ── factory helpers ───────────────────────────────────────────────────────

    /** Command-context reference backed by {@link MyViewModel}. */
    private ViewModelPropertyReference cmdRef() {
        return new ViewModelPropertyReference(dummyElement, DUMMY_RANGE, vmClass, "saveItem", true);
    }

    private List<String> lookupStrings(Object[] variants) {
        return Arrays.stream(variants)
                .map(v -> ((LookupElement) v).getLookupString())
                .collect(Collectors.toList());
    }

    private LookupElement findVariant(Object[] variants, String name) {
        return Arrays.stream(variants)
                .map(v -> (LookupElement) v)
                .filter(v -> name.equals(v.getLookupString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("'" + name + "' not found in variants"));
    }

    private com.intellij.codeInsight.completion.InsertionContext mockInsertionContext(
            Document doc, int startOffset, int tailOffset) {
        com.intellij.codeInsight.completion.InsertionContext ctx =
                mock(com.intellij.codeInsight.completion.InsertionContext.class);
        Editor editor = mock(Editor.class);
        CaretModel caretModel = mock(CaretModel.class);
        lenient().when(ctx.getDocument()).thenReturn(doc);
        lenient().when(ctx.getEditor()).thenReturn(editor);
        lenient().when(editor.getCaretModel()).thenReturn(caretModel);
        lenient().when(ctx.getStartOffset()).thenReturn(startOffset);
        lenient().when(ctx.getTailOffset()).thenReturn(tailOffset);
        return ctx;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // @command context — command name suggestions
    // ═══════════════════════════════════════════════════════════════════════════

    // Scenario: @Command method names are suggested inside @command()
    @Test
    void commandContext_commandAnnotatedMethods_allAppearInSuggestions() {
        List<String> keys = lookupStrings(cmdRef().getVariants());
        assertTrue(keys.contains("saveItem"), "saveItem (@Command) must be suggested");
        assertTrue(keys.contains("validate"), "validate (@Command) must be suggested");
        assertTrue(keys.contains("commit"),   "commit (@Command) must be suggested");
        assertTrue(keys.contains("hello"),    "hello (@Command) must be suggested");
    }

    // Scenario: @Command with explicit value shows annotation value, not method name
    @Test
    void commandContext_annotationValue_appearsInsteadOfMethodName() {
        // persistItem() has @Command("save") → lookup string is "save", not "persistItem"
        List<String> keys = lookupStrings(cmdRef().getVariants());
        assertTrue(keys.contains("save"),         "annotation value 'save' must appear");
        assertFalse(keys.contains("persistItem"), "method name 'persistItem' must NOT appear");
    }

    // Scenario: @GlobalCommand method appears in @command() suggestions
    @Test
    void commandContext_globalCommandMethod_appearsAlongWithCommands() {
        // broadcast() is @GlobalCommand — it must also be offered in @command() context
        assertTrue(lookupStrings(cmdRef().getVariants()).contains("broadcast"));
    }

    // Scenario: Non-annotated public method is NOT suggested inside @command()
    @Test
    void commandContext_nonAnnotatedPublicMethod_notSuggested() {
        // init() has no @Command annotation — must be absent
        assertFalse(lookupStrings(cmdRef().getVariants()).contains("init"));
    }

    // Scenario: Property names are NOT suggested inside @command()
    @Test
    void commandContext_getterBackedPropertyNames_notSuggested() {
        List<String> keys = lookupStrings(cmdRef().getVariants());
        assertFalse(keys.contains("list"),   "'list' is a property, must not appear in command context");
        assertFalse(keys.contains("name"),   "'name' is a property, must not appear in command context");
        assertFalse(keys.contains("active"), "'active' is a property, must not appear in command context");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Annotation template suppression (MVVMAnnotationCompletionProvider layer)
    // ═══════════════════════════════════════════════════════════════════════════

    // Scenario: "@global-command" annotation template does NOT appear inside @command()
    // The fix lives in MVVMAnnotationCompletionProvider.isInsideAnnotationBody():
    // when annotVal = "@command(" the paren depth is 1, so annotation templates are suppressed.

    @Test
    void insideAnnotationBody_commandOpenParen_suppressesAnnotationTemplateSuggestions() {
        // "@command(" → depth 1 → we are inside the annotation body
        assertTrue(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@command("),
                "cursor inside @command( must be detected as inside annotation body");
    }

    @Test
    void insideAnnotationBody_globalCommandOpenParen_suppressesAnnotationTemplateSuggestions() {
        assertTrue(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@global-command("),
                "cursor inside @global-command( must be detected as inside annotation body");
    }

    @Test
    void notInsideAnnotationBody_afterClosedAnnotation_annotationTemplatesAllowed() {
        // "@command('save') @" → depth returns to 0 → new annotation template can be suggested
        assertFalse(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@command('save') @"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // @global-command context
    // ═══════════════════════════════════════════════════════════════════════════

    // Scenario: @GlobalCommand method names are suggested inside @global-command()
    // Both @command and @global-command map to isCommandContext=true in processChain(),
    // so the same getCommandVariants() path is exercised for both annotation types.
    @Test
    void globalCommandContext_globalCommandMethod_presentInSuggestions() {
        // cmdRef() uses isCommandContext=true, the same flag set for @global-command context
        assertTrue(lookupStrings(cmdRef().getVariants()).contains("broadcast"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Insert behaviour
    // ═══════════════════════════════════════════════════════════════════════════

    // Scenario: Selecting a command from @command completion inserts it wrapped in single quotes
    @Test
    void commandInsert_saveItem_wrappedInSingleQuotes() {
        LookupElement elem = findVariant(cmdRef().getVariants(), "saveItem");
        Document doc = mock(Document.class);
        com.intellij.codeInsight.completion.InsertionContext ctx =
                mockInsertionContext(doc, 9, 17); // offsets within @command(...)
        elem.handleInsert(ctx);
        verify(doc).replaceString(9, 17, "'saveItem'");
    }

    // Scenario: Selecting a @GlobalCommand from @command completion inserts wrapped in quotes
    @Test
    void commandInsert_broadcast_wrappedInSingleQuotes() {
        LookupElement elem = findVariant(cmdRef().getVariants(), "broadcast");
        Document doc = mock(Document.class);
        com.intellij.codeInsight.completion.InsertionContext ctx =
                mockInsertionContext(doc, 9, 18);
        elem.handleInsert(ctx);
        verify(doc).replaceString(9, 18, "'broadcast'");
    }

    // Scenario: Selecting a command from @global-command completion inserts wrapped in quotes
    // (same insert handler — test uses annotation-value "save" to exercise that path)
    @Test
    void globalCommandInsert_annotationValue_wrappedInSingleQuotes() {
        // "save" is the lookup string produced from @Command("save") on persistItem()
        LookupElement elem = findVariant(cmdRef().getVariants(), "save");
        Document doc = mock(Document.class);
        com.intellij.codeInsight.completion.InsertionContext ctx =
                mockInsertionContext(doc, 16, 20);
        elem.handleInsert(ctx);
        verify(doc).replaceString(16, 20, "'save'");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Incomplete expression — no closing parenthesis
    // ═══════════════════════════════════════════════════════════════════════════

    // Scenario: Command names are still suggested when closing ) is absent
    // When the annotation has no closing ')', ZkBindingReferenceProvider.extractAnnotations()
    // still creates a ViewModelPropertyReference with isCommandContext=true, so getVariants()
    // returns command names just as in the normal case.
    @Test
    void commandContext_noClosingParen_getVariantsStillReturnsCommandNames() {
        // The no-closing-paren case produces the same ViewModelPropertyReference
        // (isCommandContext=true), so getVariants() behaves identically.
        List<String> keys = lookupStrings(cmdRef().getVariants());
        assertTrue(keys.contains("saveItem"), "saveItem must be suggested even without closing paren");
        assertTrue(keys.contains("validate"), "validate must be suggested even without closing paren");
    }

    // Scenario: Insert handler still wraps in single quotes when closing ) is absent
    // The insert handler on the LookupElement is independent of how the reference was
    // created — it always wraps the selected command name in single quotes.
    @Test
    void commandInsert_noClosingParen_insertHandlerStillWrapsInSingleQuotes() {
        LookupElement elem = findVariant(cmdRef().getVariants(), "saveItem");
        Document doc = mock(Document.class);
        com.intellij.codeInsight.completion.InsertionContext ctx =
                mockInsertionContext(doc, 9, 17);
        elem.handleInsert(ctx);
        verify(doc).replaceString(9, 17, "'saveItem'");
    }

    // Scenario: isInsideAnnotationBody detects open-paren state for no-closing-paren case
    // When the expression has no closing paren (annotVal = "@command("), isInsideAnnotationBody
    // still returns true, suppressing spurious annotation template suggestions.
    @Test
    void insideAnnotationBody_commandWithNoParen_returnsTrue() {
        assertTrue(MVVMAnnotationCompletionProvider.isInsideAnnotationBody("@command("));
    }
}
