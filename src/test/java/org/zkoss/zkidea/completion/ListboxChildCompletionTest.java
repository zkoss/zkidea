package org.zkoss.zkidea.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IntelliJ Platform test that verifies child element completion inside {@code <listbox>}
 * is restricted to schema-valid children only.
 *
 * <h3>Why BasePlatformTestCase</h3>
 * The bug cannot be caught by Mockito-based unit tests because those mock
 * {@code XmlNSDescriptorImpl} and {@code XmlElementDescriptor} entirely.
 * The real misbehaviour happens inside IntelliJ's {@code XmlElementDescriptorImpl}:
 * when it encounters {@code <xs:any namespace="##other" processContents="lax"/>}
 * inside {@code baseGroup} (which {@code listboxType} references), it expands the
 * wildcard to include ALL registered schema elements instead of only elements from
 * namespaces other than {@code http://www.zkoss.org/2005/zul}.
 *
 * <p>{@code BasePlatformTestCase} runs in a real (headless) IntelliJ application
 * context, loads the plugin's {@code plugin.xml} extensions, activates
 * {@code ZulSchemaProvider} and {@code ZkDomElementDescriptorProvider}, and then
 * invokes the real completion pipeline — reproducing exactly what the user sees.
 *
 * <h3>Expected outcome (failing until the fix)</h3>
 * <ul>
 *   <li>Valid children ({@code listitem}, {@code listhead}, etc.) must be present.</li>
 *   <li>General ZK components ({@code window}, {@code button}, {@code grid}, etc.)
 *       must NOT be present.</li>
 * </ul>
 *
 * <p>Feature file: {@code zul-code-completion.feature} — scenarios
 * "Child completion inside &lt;listbox&gt; shows only schema-valid children".
 */
public class ListboxChildCompletionTest extends BasePlatformTestCase {

    // Real ZUL files have NO xmlns declaration — IntelliJ cannot use namespace-based
    // schema lookup, so it falls back to ZkDomElementDescriptorProvider, which is
    // the exact code path that exhibits the bug (xs:any expansion shows all elements).
    // Cursor placed right after '<' inside the parent tag for element-name completion.
    private static final String LISTBOX_ZUL =
            "<zk>\n" +
            "  <listbox>\n" +
            "    <<caret>\n" +
            "  </listbox>\n" +
            "</zk>\n";

    private static final String LISTITEM_ZUL =
            "<zk>\n" +
            "  <listbox>\n" +
            "    <listitem>\n" +
            "      <<caret>\n" +
            "    </listitem>\n" +
            "  </listbox>\n" +
            "</zk>\n";

    /**
     * Completion inside {@code <listbox>} must include only schema-valid children
     * ({@code listitem}, {@code listhead}, {@code listgroup}, {@code listgroupfoot},
     * {@code frozen}, {@code auxhead}, and {@code baseGroup} members) and must NOT
     * include general ZK container components.
     *
     * <p>This test FAILS before the fix because IntelliJ's wildcard expansion causes
     * every ZK component to appear as a valid child of {@code <listbox>}.
     */
    public void testChildCompletion_insideListbox_showsOnlySchemaValidChildren() {
        myFixture.configureByText("test.zul", LISTBOX_ZUL);
        myFixture.completeBasic();

        LookupElement[] elements = myFixture.getLookupElements();
        assertNotNull("Completion list must not be null inside <listbox>", elements);

        List<String> names = Arrays.stream(elements)
                .map(LookupElement::getLookupString)
                .collect(Collectors.toList());

        // ── Valid children must be present ──────────────────────────────────
        assertTrue("listitem must appear in <listbox> completion. Actual list: " + names,
                names.contains("listitem"));
        assertTrue("listhead must appear in <listbox> completion",  names.contains("listhead"));
        assertTrue("listgroup must appear in <listbox> completion", names.contains("listgroup"));
        assertTrue("listgroupfoot must appear in <listbox> completion", names.contains("listgroupfoot"));
        assertTrue("frozen must appear in <listbox> completion",    names.contains("frozen"));
        assertTrue("auxhead must appear in <listbox> completion",   names.contains("auxhead"));

        // ── Invalid children must NOT appear ─────────────────────────────────
        // Bug: IntelliJ expands xs:any namespace="##other" in baseGroup to include
        // all registered schema elements, so every ZK component floods this list.
        assertFalse(
                "window must NOT appear in <listbox> completion — " +
                "bug: xs:any wildcard expansion in baseGroup leaks all ZK components",
                names.contains("window"));
        assertFalse(
                "button must NOT appear in <listbox> completion",
                names.contains("button"));
        assertFalse(
                "grid must NOT appear in <listbox> completion",
                names.contains("grid"));
        assertFalse(
                "selectbox must NOT appear in <listbox> completion",
                names.contains("selectbox"));
        assertFalse(
                "vlayout must NOT appear in <listbox> completion",
                names.contains("vlayout"));
        assertFalse(
                "listbox itself must NOT appear as a child of <listbox>",
                names.contains("listbox"));
    }

    /**
     * Completion inside {@code <listitem>} must show only {@code listcell} (and
     * {@code baseGroup} members), not general ZK container components.
     */
    public void testChildCompletion_insideListitem_showsOnlyListcell() {
        myFixture.configureByText("test.zul", LISTITEM_ZUL);
        myFixture.completeBasic();

        LookupElement[] elements = myFixture.getLookupElements();
        assertNotNull("Completion list must not be null inside <listitem>", elements);

        List<String> names = Arrays.stream(elements)
                .map(LookupElement::getLookupString)
                .collect(Collectors.toList());

        assertTrue("listcell must appear in <listitem> completion. Actual list: " + names,
                names.contains("listcell"));

        assertFalse(
                "window must NOT appear in <listitem> completion",
                names.contains("window"));
        assertFalse(
                "listbox must NOT appear in <listitem> completion",
                names.contains("listbox"));
        assertFalse(
                "button must NOT appear in <listitem> completion",
                names.contains("button"));
    }
}
