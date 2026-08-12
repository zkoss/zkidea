# Marketplace warning — "1 override-only API usage violation"

Report: `XmlElementDescriptor.getAttributeDescriptor(...) (1)`

## 1. The single call site

[ZulChildCompletionDescriptor.java:70](../src/main/java/org/zkoss/zkidea/dom/ZulChildCompletionDescriptor.java#L70)

```java
@Override public @Nullable XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr) { return delegate.getAttributeDescriptor(attr); }
```

This class is a hand-written decorator: it exists only to override
`getElementsDescriptors(XmlTag)` (the `xs:any namespace="##other"` completion-flood fix) and
delegates all 14 other `XmlElementDescriptor` methods verbatim. One of those verbatim
delegations is a call to an override-only method — hence the warning. **Overriding** it is
fine and required (we implement the interface); **calling** it on the delegate is not.

Pre-existing, not from the 1.0.0 preview work: the report is filed against 0.7.3, and the class
has looked like this since the completion-flood fix landed. It is a *warning*, not the
rejection — that was the separate internal-API finding fixed in `56e714b`.

## 2. Which overload is annotated — exactly one

`javap -v` on the 2023.3 SDK (`lib/app.jar`), and the same file on `intellij-community` master:

| Method | 2023.3 | master |
|---|---|---|
| `getAttributesDescriptors(XmlTag)` | clean | clean |
| `getAttributeDescriptor(String, XmlTag)` | clean (`@Nullable`, `@NonNls` only) | clean |
| `getAttributeDescriptor(XmlAttribute)` | **`@ApiStatus.OverrideOnly`** | **`@ApiStatus.OverrideOnly`** |
| `getElementsDescriptors(XmlTag)` | clean | clean |

```
# javap -v com/intellij/xml/XmlElementDescriptor.class  (ideaIC-2023.3/lib/app.jar)
public abstract com.intellij.xml.XmlAttributeDescriptor getAttributeDescriptor(com.intellij.psi.xml.XmlAttribute);
  RuntimeInvisibleAnnotations:
    org.jetbrains.annotations.ApiStatus$OverrideOnly
```

So the sibling overload `getAttributeDescriptor(String, XmlTag)` is a supported call target on
both the `sinceBuild` floor and the newest builds. That is the whole fix.

## 3. Fix — route through the name/context overload

```java
@Override public @Nullable XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr) {
    return delegate.getAttributeDescriptor(attr.getName(), attr.getParent());
}
```

`XmlAttribute.getParent()` is covariantly typed `XmlTag` in the PSI API, so this compiles with
no cast.

### Why this is zero-risk, not merely low-risk

It is *literally what the delegate does internally*. The descriptor we wrap is always the
schema-based `com.intellij.xml.impl.schema.XmlElementDescriptorImpl` (created in
[ZkDomElementDescriptorHolder.java:120-122](../src/main/java/org/zkoss/zkidea/dom/ZkDomElementDescriptorHolder.java#L120-L122)
from `XmlNSDescriptorImpl.getElementDescriptor(...)`), and both of its overloads funnel into the
same private method:

```
# javap -c com/intellij/xml/impl/schema/XmlElementDescriptorImpl.class
public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute);
   2: invokeinterface  XmlAttribute.getName:()Ljava/lang/String;
   8: invokeinterface  XmlAttribute.getParent:()Lcom/intellij/psi/xml/XmlTag;
  13: invokevirtual    getAttributeDescriptorImpl:(Ljava/lang/String;Lcom/intellij/psi/xml/XmlTag;)...

public XmlAttributeDescriptor getAttributeDescriptor(String, XmlTag);
   3: invokevirtual    getAttributeDescriptorImpl:(Ljava/lang/String;Lcom/intellij/psi/xml/XmlTag;)...
```

The new code performs the same three calls the delegate would have performed, so attribute
resolution, `@bind`-style attribute highlighting and "unknown attribute" inspections are
bit-for-bit unchanged.

### Alternatives rejected

- **Return `null`.** The platform calls this overload to resolve every attribute on a wrapped
  tag; returning `null` would make every ZUL attribute report as unknown. Silences the warning
  by breaking the feature.
- **Scan `getAttributesDescriptors(ctx)` for a name match.** Also warning-free, but it
  re-implements name matching (prefix/qualified names, `xs:anyAttribute`) instead of reusing the
  delegate's — more code, real behaviour risk.
- **Drop the decorator, subclass the delegate instead.** We cannot: the delegate is constructed
  by the platform from the XSD; there is no supported way to rebuild an equivalent instance.

## 4. Verification

1. TDD: [ZulChildCompletionDescriptorTest.java](../src/test/java/org/zkoss/zkidea/dom/ZulChildCompletionDescriptorTest.java)
   — asserts the descriptor comes back from the name/context overload, and
   `verify(delegate, never()).getAttributeDescriptor(any(XmlAttribute.class))` pins the
   override-only overload as never called. Red before the change, green after.
2. `grep -n "getAttributeDescriptor(attr" src/` → no hits.
3. `withjdk.sh 17 ./gradlew build` → compiles, full suite green.
4. Marketplace re-upload → the "Override-only method usage violation" section must be empty.
