/* ZulDomUtil.java

	Purpose:

	Description:

	History:
		Jul 10 5:48 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.dom;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;
import org.zkoss.zkidea.lang.ZulFileType;

/**
 * @author by jumperchen
 */
public class ZulDomUtil {
	private static final Logger LOG = Logger.getInstance(ZulDomUtil.class);

	public static String VIEW_MODEL = "viewModel";

	/**
	 * Shared alternation of all recognized ZK binding annotation names.
	 * Used to build {@link #BINDING_CHAIN_EXTRACT_PATTERN} and
	 * {@code ZkBindingReferenceProvider.BINDING_ANNOTATION_PATTERN} from a
	 * single source of truth so the two cannot drift.
	 */
	public static final String BINDING_ANNOTATIONS =
			"load|bind|save|init|command|global-command|converter|validator";

	/**
	 * Extracts the inner content of any recognized ZK binding annotation.
	 * E.g., {@code @load(vm.items)} → group(1) = {@code vm.items}.
	 */
	public static final Pattern BINDING_CHAIN_EXTRACT_PATTERN =
			Pattern.compile("@(?:" + BINDING_ANNOTATIONS + ")\\s*\\(([^)]*)\\)");

	private static final Pattern IDENTIFIER_CHAIN_PATTERN =
			Pattern.compile("[a-zA-Z_]\\w*(?:\\.[a-zA-Z_]\\w*)*");

	private static final Pattern ID_PATTERN =
			Pattern.compile("@id\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
	private static final Pattern INIT_PATTERN =
			Pattern.compile("@init\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

	// -------------------------------------------------------------------------
	// File-type helpers
	// -------------------------------------------------------------------------

	public static boolean isZKFile(PsiFile file) {
		if (!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return name.endsWith(ZulFileType.EXTENSION) || isZkConfigFile(file) || isLangAddonFile(file);
		}
	}

	public static boolean isZkConfigFile(PsiFile file) {
		if (!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return "zk.xml".equals(name);
		}
	}

	public static boolean isLangAddonFile(PsiFile file) {
		if (!(file instanceof XmlFile)) {
			return false;
		} else {
			String name = file.getName();
			return "lang-addon.xml".equals(name);
		}
	}

	public static boolean isZKFile(String ext) {
		return ZulFileType.EXTENSION.equalsIgnoreCase(ext) || "zhtml".equalsIgnoreCase(ext);
	}

	public static boolean hasViewModel(PsiElement element) {
		do {
			if (element instanceof XmlTag) {
				if (hasAttribute((XmlTag) element, VIEW_MODEL)) {
					return true;
				}
			}
			element = element.getParent();
		} while (element != null);
		return false;
	}

	public static boolean hasAttribute(XmlTag tag, String key) {
		return tag.getAttribute(key) != null;
	}

	/**
	 * Finds the nearest ancestor XmlTag that has a viewModel attribute.
	 *
	 * @return the XmlTag with the viewModel attribute, or null if not found
	 */
	@Nullable
	public static XmlTag findViewModelTag(PsiElement element) {
		while (element != null) {
			if (element instanceof XmlTag) {
				if (hasAttribute((XmlTag) element, VIEW_MODEL)) {
					return (XmlTag) element;
				}
			}
			element = element.getParent();
		}
		return null;
	}

	/**
	 * Extracts the @id value from a viewModel attribute string.
	 * E.g., "@id('vm') @init('com.example.VM')" returns "vm".
	 */
	@Nullable
	public static String extractViewModelId(String viewModelAttrValue) {
		if (viewModelAttrValue == null) return null;
		Matcher matcher = ID_PATTERN.matcher(viewModelAttrValue);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * Extracts the class name from @init in a viewModel attribute string.
	 * E.g., "@id('vm') @init('com.example.VM')" returns "com.example.VM".
	 */
	@Nullable
	public static String extractViewModelClassName(String viewModelAttrValue) {
		if (viewModelAttrValue == null) return null;
		Matcher matcher = INIT_PATTERN.matcher(viewModelAttrValue);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * Resolves the ViewModel PsiClass from a viewModel attribute value.
	 */
	@Nullable
	public static PsiClass resolveViewModelClass(Project project, String viewModelAttrValue) {
		String className = extractViewModelClassName(viewModelAttrValue);
		if (className == null) return null;
		return JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
	}

	// -------------------------------------------------------------------------
	// Scope variable declaration lookup
	// -------------------------------------------------------------------------

	/**
	 * Walks up PSI ancestors to find a {@code <template var="varName">} attribute.
	 *
	 * @return the {@code var} XmlAttribute, or null if not found
	 */
	@Nullable
	public static XmlAttribute findTemplateVarDeclaration(PsiElement context, String varName) {
		PsiElement el = context;
		while (el != null) {
			if (el instanceof XmlTag) {
				XmlTag tag = (XmlTag) el;
				if ("template".equals(tag.getLocalName())) {
					XmlAttribute varAttr = tag.getAttribute("var");
					if (varAttr != null && varName.equals(varAttr.getValue())) {
						LOG.debug("findTemplateVarDeclaration: found var='" + varName + "'");
						return varAttr;
					}
				}
			}
			el = el.getParent();
		}
		return null;
	}

	/**
	 * Walks up PSI ancestors to find an {@code <apply attrName="...">} attribute
	 * whose local name matches {@code varName}.
	 *
	 * @return the matching XmlAttribute, or null if not found
	 */
	@Nullable
	public static XmlAttribute findApplyAttributeDeclaration(PsiElement context, String varName) {
		PsiElement el = context;
		while (el != null) {
			if (el instanceof XmlTag) {
				XmlTag tag = (XmlTag) el;
				if ("apply".equals(tag.getLocalName())) {
					XmlAttribute attr = tag.getAttribute(varName);
					if (attr != null) {
						LOG.debug("findApplyAttributeDeclaration: found '" + varName + "' in apply tag");
						return attr;
					}
				}
			}
			el = el.getParent();
		}
		return null;
	}

	/**
	 * Walks up PSI ancestors to find a {@code forEachVar="varName"} attribute.
	 *
	 * @return the {@code forEachVar} XmlAttribute, or null if not found
	 */
	@Nullable
	public static XmlAttribute findForEachVarDeclaration(PsiElement context, String varName) {
		PsiElement el = context;
		while (el != null) {
			if (el instanceof XmlTag) {
				XmlTag tag = (XmlTag) el;
				XmlAttribute forEachVar = tag.getAttribute("forEachVar");
				if (forEachVar != null && varName.equals(forEachVar.getValue())) {
					LOG.debug("findForEachVarDeclaration: found forEachVar='" + varName + "'");
					return forEachVar;
				}
			}
			el = el.getParent();
		}
		return null;
	}

	/**
	 * Finds the scope variable declaration for {@code varName}.
	 * Tries template {@code var}, apply attribute, and {@code forEachVar} in order.
	 *
	 * @return the declaring XmlAttribute, or null if not found
	 */
	@Nullable
	public static XmlAttribute findScopeVariableDeclaration(PsiElement context, String varName) {
		XmlAttribute result = findTemplateVarDeclaration(context, varName);
		if (result != null) return result;
		result = findApplyAttributeDeclaration(context, varName);
		if (result != null) return result;
		return findForEachVarDeclaration(context, varName);
	}

	// -------------------------------------------------------------------------
	// Scope variable type resolution
	// -------------------------------------------------------------------------

	/**
	 * Resolves the Java type of a scope variable given its declaring attribute.
	 * Dispatches to the appropriate resolver based on the attribute kind.
	 */
	@Nullable
	public static PsiClass resolveScopeVariableType(XmlAttribute declAttr, String vmId,
	                                                PsiClass vmClass, PsiElement context) {
		if (declAttr == null) return null;
		XmlTag ownerTag = declAttr.getParent();
		if (ownerTag == null) return null;
		String attrName = declAttr.getLocalName();
		String tagName = ownerTag.getLocalName();
		if ("template".equals(tagName) && "var".equals(attrName)) {
			return resolveTemplateVarType(declAttr, vmId, vmClass, context);
		} else if ("forEachVar".equals(attrName)) {
			return resolveForEachVarType(declAttr, vmId, vmClass, context);
		}
		return resolveApplyPassdownType(declAttr, vmId, vmClass, context);
	}

	/**
	 * Resolves the element type of a {@code <template var="...">} variable.
	 * <p>
	 * First checks for an explicit {@code type="..."} attribute on the template tag
	 * and resolves the class directly. Falls back to inferring the element type
	 * from a {@code model} attribute on an ancestor tag.
	 */
	@Nullable
	public static PsiClass resolveTemplateVarType(XmlAttribute varAttr, String vmId,
	                                              PsiClass vmClass, PsiElement context) {
		XmlTag templateTag = varAttr.getParent();
		if (templateTag == null) return null;

		// Check for explicit type attribute (Task 1.3)
		String explicitType = templateTag.getAttributeValue("type");
		if (explicitType != null && !explicitType.isEmpty()) {
			LOG.debug("resolveTemplateVarType: resolving explicit type=" + explicitType);
			PsiClass resolved = JavaPsiFacade.getInstance(context.getProject())
					.findClass(explicitType, GlobalSearchScope.allScope(context.getProject()));
			if (resolved != null) return resolved;
			LOG.warn("resolveTemplateVarType: could not find class '" + explicitType + "'");
		}

		// Fallback: look for model attribute on ancestor tags
		XmlTag ancestor = templateTag.getParentTag();
		while (ancestor != null) {
			String modelAttrValue = ancestor.getAttributeValue("model");
			if (modelAttrValue != null) {
				LOG.debug("resolveTemplateVarType: inferring from model=" + modelAttrValue);
				return resolveCollectionElementType(modelAttrValue, vmId, vmClass, context);
			}
			ancestor = ancestor.getParentTag();
		}
		LOG.debug("resolveTemplateVarType: no type or model found for template var");
		return null;
	}

	/**
	 * Resolves the type of an {@code <apply>} pass-down attribute by parsing
	 * the binding chain from the attribute value.
	 */
	@Nullable
	public static PsiClass resolveApplyPassdownType(XmlAttribute applyAttr, String vmId,
	                                                PsiClass vmClass, PsiElement context) {
		if (applyAttr == null) return null;
		String attrValue = applyAttr.getValue();
		if (attrValue == null) return null;

		Matcher m = BINDING_CHAIN_EXTRACT_PATTERN.matcher(attrValue);
		String chain;
		if (m.find()) {
			String innerContent = m.group(1).trim();
			Matcher chainMatcher = IDENTIFIER_CHAIN_PATTERN.matcher(innerContent);
			if (!chainMatcher.find()) return null;
			chain = chainMatcher.group();
		} else {
			// May be a plain chain without an annotation wrapper
			Matcher chainMatcher = IDENTIFIER_CHAIN_PATTERN.matcher(attrValue.trim());
			if (!chainMatcher.find()) return null;
			chain = chainMatcher.group();
		}
		LOG.debug("resolveApplyPassdownType: resolving chain=" + chain);
		return resolveChainFinalType(chain, vmId, vmClass, context);
	}

	/**
	 * Resolves the element type of a {@code forEachVar} variable by reading the
	 * {@code forEach} attribute from the owning tag and unwrapping its collection type.
	 */
	@Nullable
	public static PsiClass resolveForEachVarType(XmlAttribute forEachVarAttr, String vmId,
	                                             PsiClass vmClass, PsiElement context) {
		XmlTag ownerTag = forEachVarAttr.getParent();
		if (ownerTag == null) return null;
		String forEachValue = ownerTag.getAttributeValue("forEach");
		if (forEachValue == null) return null;
		LOG.debug("resolveForEachVarType: forEach=" + forEachValue);
		return resolveCollectionElementType(forEachValue, vmId, vmClass, context);
	}

	// -------------------------------------------------------------------------
	// Private chain-resolution helpers
	// -------------------------------------------------------------------------

	/**
	 * Resolves the collection element type from a binding expression that contains
	 * a collection-typed chain (e.g., {@code @load(vm.items)} → element type of items).
	 */
	@Nullable
	private static PsiClass resolveCollectionElementType(String bindingExpr, String vmId,
	                                                     PsiClass vmClass, PsiElement context) {
		Matcher m = BINDING_CHAIN_EXTRACT_PATTERN.matcher(bindingExpr);
		String chain;
		if (m.find()) {
			String inner = m.group(1).trim();
			Matcher cm = IDENTIFIER_CHAIN_PATTERN.matcher(inner);
			if (!cm.find()) return null;
			chain = cm.group();
		} else {
			Matcher cm = IDENTIFIER_CHAIN_PATTERN.matcher(bindingExpr.trim());
			if (!cm.find()) return null;
			chain = cm.group();
		}
		return resolveChainElementType(chain, vmId, vmClass, context);
	}

	/**
	 * Resolves a dotted chain to its final element type, unwrapping generic
	 * collection parameters ({@code List<T>} → {@code T}) or array component
	 * types ({@code T[]} → {@code T}) for the last segment.
	 */
	@Nullable
	private static PsiClass resolveChainElementType(String chain, String vmId,
	                                                PsiClass vmClass, PsiElement context) {
		String[] segments = chain.split("\\.");
		if (segments.length < 2 || !segments[0].equals(vmId) || vmClass == null) return null;

		PsiClass currentClass = vmClass;
		PsiType lastType = null;
		for (int i = 1; i < segments.length; i++) {
			PsiMethod getter = findGetter(currentClass, segments[i]);
			if (getter == null) return null;
			lastType = getter.getReturnType();
			if (lastType == null) return null;
			if (i < segments.length - 1) {
				currentClass = resolveTypeToClass(lastType, context);
				if (currentClass == null) return null;
			}
		}
		if (lastType == null) return null;

		// Unwrap generic parameter: List<T> → T
		if (lastType instanceof PsiClassType) {
			PsiType[] params = ((PsiClassType) lastType).getParameters();
			if (params.length > 0 && params[0] instanceof PsiClassType) {
				return ((PsiClassType) params[0]).resolve();
			}
		}
		// Unwrap array: T[] → T
		if (lastType instanceof PsiArrayType) {
			PsiType component = ((PsiArrayType) lastType).getComponentType();
			if (component instanceof PsiClassType) {
				return ((PsiClassType) component).resolve();
			}
		}
		return resolveTypeToClass(lastType, context);
	}

	/**
	 * Resolves a dotted chain to the {@link PsiClass} of its final segment
	 * (no generic unwrapping — used when the chain value IS the desired type).
	 */
	@Nullable
	private static PsiClass resolveChainFinalType(String chain, String vmId,
	                                              PsiClass vmClass, PsiElement context) {
		String[] segments = chain.split("\\.");
		if (segments.length < 2 || !segments[0].equals(vmId) || vmClass == null) return null;

		PsiClass currentClass = vmClass;
		for (int i = 1; i < segments.length; i++) {
			PsiMethod getter = findGetter(currentClass, segments[i]);
			if (getter == null) return null;
			PsiType returnType = getter.getReturnType();
			if (returnType == null) return null;
			currentClass = resolveTypeToClass(returnType, context);
			if (currentClass == null) return null;
		}
		return currentClass;
	}

	/**
	 * Resolves a {@link PsiType} to its raw {@link PsiClass}, stripping generic parameters.
	 */
	@Nullable
	private static PsiClass resolveTypeToClass(PsiType type, PsiElement context) {
		if (type == null) return null;
		String canonicalText = type.getCanonicalText();
		int genericIndex = canonicalText.indexOf('<');
		if (genericIndex > 0) {
			canonicalText = canonicalText.substring(0, genericIndex);
		}
		// Strip array suffix
		if (canonicalText.endsWith("[]")) {
			canonicalText = canonicalText.substring(0, canonicalText.length() - 2);
		}
		return JavaPsiFacade.getInstance(context.getProject())
				.findClass(canonicalText, GlobalSearchScope.allScope(context.getProject()));
	}

	/**
	 * Finds the getter method for a property name on the given class.
	 * Looks for {@code getXxx()} first, then {@code isXxx()}.
	 */
	@Nullable
	static PsiMethod findGetter(PsiClass psiClass, String property) {
		if (psiClass == null || property == null || property.isEmpty()) return null;
		String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
		String getterName = "get" + capitalized;
		String boolGetterName = "is" + capitalized;
		for (PsiMethod method : psiClass.getAllMethods()) {
			if (!method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
			if (method.getParameterList().getParametersCount() != 0) continue;
			String name = method.getName();
			if (name.equals(getterName) || name.equals(boolGetterName)) {
				return method;
			}
		}
		return null;
	}
}
