package org.zkoss.zkidea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ISA-level integration tests for command binding references emitted by
 * {@link ZkBindingReferenceProvider#getReferencesByElement}.
 * Covers Groups 7–9 from command-binding-navigation.isa.feature.
 *
 * <ul>
 *   <li>Group 7 — Pass 2: {@code @command('literal')} / {@code @global-command('literal')}
 *                creates {@link ZkCommandReference} with the correct TextRange</li>
 *   <li>Group 8 — Pass 1 before/after: {@code before='name'} / {@code after='name'}
 *                inside an annotation body creates {@link ZkCommandReference}</li>
 *   <li>Group 9 — negative / guard cases specific to command binding</li>
 * </ul>
 *
 * <p>TextRange convention (valueOffset = 1):
 * <pre>
 *   @command('saveItem')                   group(1) start=10, end=18 → TextRange(11, 19)
 *   @command('delete')                     group(1) start=10, end=16 → TextRange(11, 17)
 *   @global-command('refresh')             group(1) start=17, end=24 → TextRange(18, 25)
 *
 *   @save(vm.name, before='validate')
 *     body="vm.name, before='validate'", bodyStartOffset=6, bodyOffsetInElement=7
 *     BEFORE_AFTER group(1) start=17, end=25  → cmdStart=24, cmdEnd=32  → TextRange(24, 32)
 *
 *   @save(vm.name, after='commit')
 *     body="vm.name, after='commit'",   bodyStartOffset=6, bodyOffsetInElement=7
 *     BEFORE_AFTER group(1) start=16, end=22  → cmdStart=23, cmdEnd=29  → TextRange(23, 29)
 * </pre>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ZkBindingReferenceProviderCommandTest {

    private final ZkBindingReferenceProvider provider = new ZkBindingReferenceProvider();

    // ─── helpers (mirrors ZkBindingReferenceProviderMvvmPropertyTest) ─────────

    /**
     * Creates a mock {@link XmlAttributeValue} whose text-range metadata gives
     * {@code valueOffset = 1}:
     * <pre>
     *   getTextRange()      = TextRange(0, len+2)
     *   getValueTextRange() = TextRange(1, len+1)
     * </pre>
     */
    private XmlAttributeValue zulAttr(String value) {
        XmlAttributeValue attr = mock(XmlAttributeValue.class);
        XmlFile mockFile = mock(XmlFile.class);
        when(attr.getContainingFile()).thenReturn(mockFile);
        lenient().when(attr.getValue()).thenReturn(value);
        lenient().when(attr.getTextRange())
                .thenReturn(new TextRange(0, value.length() + 2));
        lenient().when(attr.getValueTextRange())
                .thenReturn(new TextRange(1, value.length() + 1));
        return attr;
    }

    /** Stubs the four ZulDomUtil static gates. */
    private void setupVmMocks(MockedStatic<ZulDomUtil> util, String vmId, PsiClass vmClass) {
        XmlTag mockTag = mock(XmlTag.class);
        when(mockTag.getAttributeValue(ZulDomUtil.VIEW_MODEL))
                .thenReturn("@id('" + vmId + "') @init('com.example.MyViewModel')");
        util.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);
        util.when(() -> ZulDomUtil.findViewModelTag(any())).thenReturn(mockTag);
        util.when(() -> ZulDomUtil.extractViewModelId(any())).thenReturn(vmId);
        util.when(() -> ZulDomUtil.resolveViewModelClass(any(), any())).thenReturn(vmClass);
    }

    /**
     * Reads a private field from an object via reflection (used for {@code commandName}).
     */
    @SuppressWarnings("unchecked")
    private static <T> T privateField(Object obj, String fieldName) {
        try {
            Class<?> cls = obj.getClass();
            while (cls != null) {
                try {
                    java.lang.reflect.Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return (T) f.get(obj);
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }
            throw new AssertionError("Field '" + fieldName + "' not found on " + obj.getClass());
        } catch (IllegalAccessException e) {
            throw new AssertionError("Cannot read field '" + fieldName + "'", e);
        }
    }

    /** Finds the first reference at the given range, or fails. */
    private static PsiReference refAtRange(PsiReference[] refs, TextRange range) {
        for (PsiReference ref : refs) {
            if (range.equals(ref.getRangeInElement())) return ref;
        }
        fail("No reference found at " + range + "; available: " + rangesString(refs));
        return null; // unreachable
    }

    private static String rangesString(PsiReference[] refs) {
        StringBuilder sb = new StringBuilder("[");
        for (PsiReference r : refs) sb.append(r.getRangeInElement()).append(", ");
        return sb.append("]").toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 7  getReferencesByElement — Pass 2: @command / @global-command literal
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void group7_commandSaveItem_emitsExactlyOneZkCommandReference() {
        // "@command('saveItem')"
        //   Pass 1: body="'saveItem'" starts with quote → skipped
        //   Pass 2: COMMAND_STRING_PATTERN → group(1) start=10, end=18
        //           TextRange(1+10, 1+18) = TextRange(11, 19)
        XmlAttributeValue attr = zulAttr("@command('saveItem')");
        PsiClass mockVm = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", mockVm);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(1, refs.length, "Expected exactly 1 reference for @command('saveItem')");
            assertInstanceOf(ZkCommandReference.class, refs[0]);
            assertEquals(new TextRange(11, 19), refs[0].getRangeInElement());
            assertEquals("saveItem", privateField(refs[0], "commandName"));
        }
    }

    @Test
    void group7_commandDelete_emitsZkCommandReferenceAtCorrectRange() {
        // "@command('delete')" → group(1) start=10, end=16 → TextRange(11, 17)
        XmlAttributeValue attr = zulAttr("@command('delete')");
        PsiClass mockVm = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", mockVm);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            PsiReference cmdRef = refAtRange(refs, new TextRange(11, 17));
            assertInstanceOf(ZkCommandReference.class, cmdRef);
            assertEquals("delete", privateField(cmdRef, "commandName"));
        }
    }

    @Test
    void group7_globalCommandRefresh_emitsZkCommandReferenceAtCorrectRange() {
        // "@global-command('refresh')"
        //   '@global-command(' = 16 chars (0-15), "'" at 16, 'r' at 17
        //   group(1) start=17, end=24 → TextRange(18, 25)
        XmlAttributeValue attr = zulAttr("@global-command('refresh')");
        PsiClass mockVm = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", mockVm);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            PsiReference cmdRef = refAtRange(refs, new TextRange(18, 25));
            assertInstanceOf(ZkCommandReference.class, cmdRef);
            assertEquals("refresh", privateField(cmdRef, "commandName"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 8  getReferencesByElement — Pass 1 before/after guards
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void group8_saveWithBefore_emitsThreeRefsIncludingZkCommandReference() {
        // "@save(vm.name, before='validate')"
        //   body="vm.name, before='validate'", bodyStartOffset=6, bodyOffsetInElement=7
        //   refs[0]: ViewModelIdReference      TextRange(7, 9)   [vm]
        //   refs[1]: ViewModelPropertyReference TextRange(10, 14) [name]
        //   refs[2]: ZkCommandReference         TextRange(24, 32) [validate]
        //     Derivation: group(1) start=17, end=25 in body → 7+17=24, 7+25=32
        XmlAttributeValue attr = zulAttr("@save(vm.name, before='validate')");
        PsiClass mockVm = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", mockVm);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(3, refs.length);

            assertInstanceOf(ViewModelIdReference.class, refs[0]);
            assertEquals(new TextRange(7, 9), refs[0].getRangeInElement());

            assertInstanceOf(ViewModelPropertyReference.class, refs[1]);
            assertEquals(new TextRange(10, 14), refs[1].getRangeInElement());

            assertInstanceOf(ZkCommandReference.class, refs[2]);
            assertEquals(new TextRange(24, 32), refs[2].getRangeInElement());
            assertEquals("validate", privateField(refs[2], "commandName"));
        }
    }

    @Test
    void group8_saveWithAfter_emitsThreeRefsIncludingZkCommandReference() {
        // "@save(vm.name, after='commit')"
        //   body="vm.name, after='commit'", bodyStartOffset=6, bodyOffsetInElement=7
        //   refs[0]: ViewModelIdReference      TextRange(7, 9)
        //   refs[1]: ViewModelPropertyReference TextRange(10, 14) [name]
        //   refs[2]: ZkCommandReference         TextRange(23, 29) [commit]
        //     Derivation: group(1) start=16, end=22 in body → 7+16=23, 7+22=29
        XmlAttributeValue attr = zulAttr("@save(vm.name, after='commit')");
        PsiClass mockVm = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", mockVm);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            assertEquals(3, refs.length);

            assertInstanceOf(ZkCommandReference.class, refs[2]);
            assertEquals(new TextRange(23, 29), refs[2].getRangeInElement());
            assertEquals("commit", privateField(refs[2], "commandName"));
        }
    }

    @Test
    void group8_bindWithBefore_emitsZkCommandReference() {
        // "@bind(vm.name, before='validateName')"
        //   body="vm.name, before='validateName'", bodyStartOffset=6, bodyOffsetInElement=7
        //   "validateName" len=12, group(1) start=17, end=29 in body
        //   → TextRange(7+17, 7+29) = TextRange(24, 36)
        XmlAttributeValue attr = zulAttr("@bind(vm.name, before='validateName')");
        PsiClass mockVm = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", mockVm);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            boolean hasCmdRef = false;
            for (PsiReference ref : refs) {
                if (ref instanceof ZkCommandReference) {
                    assertEquals("validateName", privateField(ref, "commandName"));
                    hasCmdRef = true;
                }
            }
            assertTrue(hasCmdRef, "Expected at least one ZkCommandReference for before='validateName'");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GROUP 9  getReferencesByElement — negative / guard cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void group9_plainLoadAnnotation_emitsNoZkCommandReference() {
        // "@load(vm.list)" produces only ViewModelIdReference + ViewModelPropertyReference;
        // COMMAND_STRING_PATTERN does not match @load so no ZkCommandReference.
        XmlAttributeValue attr = zulAttr("@load(vm.list)");
        PsiClass mockVm = mock(PsiClass.class);

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", mockVm);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());

            for (PsiReference ref : refs) {
                assertFalse(ref instanceof ZkCommandReference,
                        "Unexpected ZkCommandReference for @load(vm.list)");
            }
        }
    }

    @Test
    void group9_vmClassNull_returnsEmptyArray() {
        // resolveViewModelClass returns null → method returns EMPTY_ARRAY before Pass 2
        XmlAttributeValue attr = zulAttr("@command('saveItem')");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            setupVmMocks(util, "vm", null);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());
            assertSame(PsiReference.EMPTY_ARRAY, refs);
        }
    }

    @Test
    void group9_noViewModelAncestorTag_returnsEmptyArray() {
        // findViewModelTag returns null → EMPTY_ARRAY
        XmlAttributeValue attr = zulAttr("@command('saveItem')");

        try (MockedStatic<ZulDomUtil> util = mockStatic(ZulDomUtil.class)) {
            util.when(() -> ZulDomUtil.isZKFile(any(PsiFile.class))).thenReturn(true);
            util.when(() -> ZulDomUtil.findViewModelTag(any())).thenReturn(null);

            PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());
            assertSame(PsiReference.EMPTY_ARRAY, refs);
        }
    }

    @Test
    void group9_nonZulFile_returnsEmptyArray() {
        // A plain PsiFile (not XmlFile) causes ZulDomUtil.isZKFile to return false
        XmlAttributeValue attr   = mock(XmlAttributeValue.class);
        PsiFile nonXmlFile       = mock(PsiFile.class);
        when(attr.getContainingFile()).thenReturn(nonXmlFile);

        PsiReference[] refs = provider.getReferencesByElement(attr, new ProcessingContext());
        assertSame(PsiReference.EMPTY_ARRAY, refs);
    }
}
