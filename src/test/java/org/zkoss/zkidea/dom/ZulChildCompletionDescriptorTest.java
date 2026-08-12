package org.zkoss.zkidea.dom;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Marketplace verifier report: "1 override-only API usage violation —
 * XmlElementDescriptor.getAttributeDescriptor(...)".
 *
 * <p>{@code XmlElementDescriptor#getAttributeDescriptor(XmlAttribute)} is annotated
 * {@code @ApiStatus.OverrideOnly} (verified in both the 2023.3 SDK bytecode and on
 * intellij-community master); its sibling {@code getAttributeDescriptor(String, XmlTag)} is not.
 * {@link ZulChildCompletionDescriptor} must therefore <em>override</em> the annotated overload —
 * the platform calls it to resolve every attribute on a wrapped tag — but must never
 * <em>call</em> it on the delegate.
 *
 * <p>Routing through the name/context overload is behaviour-preserving rather than merely
 * warning-silencing: the schema descriptor we wrap implements the annotated overload as exactly
 * {@code getAttributeDescriptorImpl(attribute.getName(), attribute.getParent())}, which is also
 * what the name/context overload calls.
 */
class ZulChildCompletionDescriptorTest {

    @Test
    void anAttributeIsResolvedThroughTheOverloadThatIsNotOverrideOnly() {
        XmlElementDescriptor delegate = mock(XmlElementDescriptor.class);
        XmlTag windowTag = mock(XmlTag.class);
        XmlAttribute applyAttribute = mock(XmlAttribute.class);
        XmlAttributeDescriptor expected = mock(XmlAttributeDescriptor.class);

        when(applyAttribute.getName()).thenReturn("apply");
        when(applyAttribute.getParent()).thenReturn(windowTag);
        when(delegate.getAttributeDescriptor("apply", windowTag)).thenReturn(expected);

        XmlAttributeDescriptor result =
                new ZulChildCompletionDescriptor(delegate).getAttributeDescriptor(applyAttribute);

        assertSame(expected, result,
                "the wrapper must resolve the attribute via delegate.getAttributeDescriptor(name, context)");
        verify(delegate, never()).getAttributeDescriptor(any(XmlAttribute.class));
    }
}
