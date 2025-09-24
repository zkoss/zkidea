/* ZulDomElementDescriptorHolder.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 5:46 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zkoss.zkidea.lang.*;
import org.zkoss.zkidea.lang.ZkConfigSchemaProvider;
import org.zkoss.zkidea.lang.LangAddonSchemaProvider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Project-level service that manages and caches XML element descriptors for ZK Framework files.
 * This service provides schema-based validation and code completion for both ZUL files and
 * ZK configuration files (zk.xml) with automatic default namespace support.
 *
 * <p>This class acts as a bridge between IntelliJ's XML infrastructure and ZK-specific schemas,
 * enabling seamless code completion and validation without requiring explicit namespace declarations
 * in ZK files.</p>
 *
 * <h3>Architecture and IntelliJ Integration:</h3>
 * <ol>
 *   <li><strong>Service Registration:</strong> Registered as a project-level service in plugin.xml</li>
 *   <li><strong>File Type Detection:</strong> Uses {@link ZulDomUtil#isZKFile} to identify ZK files</li>
 *   <li><strong>Schema Resolution:</strong> Loads XSD schemas via {@link ExternalResourceManager}</li>
 *   <li><strong>Descriptor Caching:</strong> Uses {@link CachedValue} for performance optimization</li>
 *   <li><strong>Default Namespace:</strong> Provides automatic namespace resolution like native XML support</li>
 * </ol>
 *
 * <h3>Supported File Types:</h3>
 * <ul>
 *   <li><strong>ZUL_FILE:</strong> .zul files using zul.xsd schema with namespace "http://www.zkoss.org/2005/zul"</li>
 *   <li><strong>ZK_CONFIG_FILE:</strong> zk.xml files using zk.xsd schema with namespace "http://www.zkoss.org/2005/zk/config"</li>
 *   <li><strong>LANG_ADDON_FILE:</strong> lang-addon.xml files using lang-addon.xsd schema with namespace "http://www.zkoss.org/2005/zk/lang-addon"</li>
 * </ul>
 *
 * <h3>How Default Namespace Works:</h3>
 * <p>Similar to how IntelliJ handles HTML files without explicit namespace declarations,
 * this service enables ZK files to work without {@code xmlns} declarations by:</p>
 * <ol>
 *   <li>Detecting file types based on filename/extension patterns</li>
 *   <li>Loading appropriate XSD schema descriptors</li>
 *   <li>Calling {@code XmlNSDescriptorImpl.getElementDescriptor(tagName, defaultNamespace)}</li>
 *   <li>Providing schema-aware completion and validation automatically</li>
 * </ol>
 *
 * <h3>Performance Considerations:</h3>
 * <ul>
 *   <li>XSD descriptors are cached per project using {@link CachedValuesManager}</li>
 *   <li>Cache invalidation tied to {@link PsiModificationTracker#MODIFICATION_COUNT}</li>
 *   <li>Thread-safe descriptor creation with synchronized access</li>
 * </ul>
 *
 * @see ExternalResourceManager IntelliJ service for resolving schema locations
 * @see XmlNSDescriptorImpl IntelliJ's XSD-based namespace descriptor implementation
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/xml-dom-api.html">IntelliJ Platform XML DOM API</a>
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-services.html">IntelliJ Platform Plugin Services</a>
 * @author jumperchen
 */
public class ZkDomElementDescriptorHolder {
	private static final Logger LOG = Logger.getInstance(ZkDomElementDescriptorHolder.class);
	private final Project myProject;
	private final Map<ZkDomElementDescriptorHolder.FileKind, CachedValue<XmlNSDescriptorImpl>> myDescriptorsMap = new HashMap<>();

	public ZkDomElementDescriptorHolder(Project project) {
		this.myProject = project;
	}

	public static ZkDomElementDescriptorHolder getInstance(@NotNull Project project) {
		return project.getService(ZkDomElementDescriptorHolder.class);
	}

	@Nullable
	public XmlElementDescriptor getDescriptor(@NotNull XmlTag tag) {
		ZkDomElementDescriptorHolder.FileKind kind = getFileKind(tag.getContainingFile());
		if (kind == null) {
			return null;
		} else {
			XmlNSDescriptorImpl desc;
			synchronized (this) {
				desc = this.tryGetOrCreateDescriptor(kind);
				if (desc == null) {
					return null;
				}
			}

			LOG.assertTrue(tag.isValid());
			LOG.assertTrue(desc.isValid());
			return desc.getElementDescriptor(tag.getName(), desc.getDefaultNamespace());
		}
	}

	@Nullable
	private XmlNSDescriptorImpl tryGetOrCreateDescriptor(final ZkDomElementDescriptorHolder.FileKind kind) {
		CachedValue<XmlNSDescriptorImpl> result = this.myDescriptorsMap.get(kind);
		if (result == null) {
			result = CachedValuesManager.getManager(this.myProject)
					.createCachedValue(() ->
							CachedValueProvider.Result.create(ZkDomElementDescriptorHolder.this.doCreateDescriptor(kind),
									PsiModificationTracker.MODIFICATION_COUNT), false);
			this.myDescriptorsMap.put(kind, result);
		}

		return result.getValue();
	}

	@Nullable
	private XmlNSDescriptorImpl doCreateDescriptor(ZkDomElementDescriptorHolder.FileKind kind) {
		String schemaUrl = kind.getSchemaUrl();
		String location = ExternalResourceManager.getInstance().getResourceLocation(schemaUrl, "");
		if (schemaUrl.equals(location)) {
			return null;
		} else {
			VirtualFile schema;
			try {
				schema = VfsUtil.findFileByURL(new URL(location));
			} catch (MalformedURLException var7) {
				return null;
			}
			if (schema == null) {
				return null;
			} else {
				PsiFile psiFile = PsiManager.getInstance(this.myProject).findFile(schema);
				if (!(psiFile instanceof XmlFile)) {
					return null;
				} else {
					XmlNSDescriptorImpl result = new XmlNSDescriptorImpl();
					result.init(psiFile);
					return result;
				}
			}
		}
	}

	@Nullable
	private static ZkDomElementDescriptorHolder.FileKind getFileKind(PsiFile file) {
		if (ZulDomUtil.isLangAddonFile(file)) {
			return FileKind.LANG_ADDON_FILE;
		} else if (ZulDomUtil.isZkConfigFile(file)) {
			return FileKind.ZK_CONFIG_FILE;
		} else if (ZulDomUtil.isZKFile(file)) {
			return FileKind.ZUL_FILE;
		} else {
			return null;
		}
	}

	private enum FileKind {
		ZUL_FILE {
			public String getSchemaUrl() {
				return ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL;
			}
		},
		ZK_CONFIG_FILE {
			public String getSchemaUrl() {
				return ZkConfigSchemaProvider.ZK_CONFIG_PROJECT_SCHEMA_URL;
			}
		},
		LANG_ADDON_FILE {
			public String getSchemaUrl() {
				return LangAddonSchemaProvider.LANG_ADDON_SCHEMA_URL;
			}
		};

		FileKind() {
		}

		public abstract String getSchemaUrl();
	}
}