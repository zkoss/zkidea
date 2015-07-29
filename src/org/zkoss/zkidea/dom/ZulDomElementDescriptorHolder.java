/* ZulDomElementDescriptorHolder.java

	Purpose:
		
	Description:
		
	History:
		Jul 10 5:46 PM 2015, Created by jumperchen

Copyright (C) 2015 Potix Corporation. All Rights Reserved.

*/
package org.zkoss.zkidea.dom;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.ServiceManager;
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
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zkoss.zkidea.lang.ZulSchemaProvider;

/**
 * @author by jumperchen
 */
public class ZulDomElementDescriptorHolder {
	private static final Logger LOG = Logger.getInstance(ZulDomElementDescriptorHolder.class);
	private final Project myProject;
	private final Map<ZulDomElementDescriptorHolder.FileKind, CachedValue<XmlNSDescriptorImpl>> myDescriptorsMap = new THashMap();

	public ZulDomElementDescriptorHolder(Project project) {
		this.myProject = project;
	}

	public static ZulDomElementDescriptorHolder getInstance(@NotNull Project project) {
		return (ZulDomElementDescriptorHolder) ServiceManager.getService(project, ZulDomElementDescriptorHolder.class);
	}

	@Nullable
	public XmlElementDescriptor getDescriptor(@NotNull XmlTag tag) {
		ZulDomElementDescriptorHolder.FileKind kind = getFileKind(tag.getContainingFile());
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
	private XmlNSDescriptorImpl tryGetOrCreateDescriptor(final ZulDomElementDescriptorHolder.FileKind kind) {
		CachedValue result = (CachedValue) this.myDescriptorsMap.get(kind);
		if (result == null) {
			result = CachedValuesManager.getManager(this.myProject).createCachedValue(new CachedValueProvider() {
				public Result<XmlNSDescriptorImpl> compute() {
					return Result.create(ZulDomElementDescriptorHolder.this.doCreateDescriptor(kind), new Object[]{PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT});
				}
			}, false);
			this.myDescriptorsMap.put(kind, result);
		}

		return (XmlNSDescriptorImpl) result.getValue();
	}

	@Nullable
	private XmlNSDescriptorImpl doCreateDescriptor(ZulDomElementDescriptorHolder.FileKind kind) {
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
	private static ZulDomElementDescriptorHolder.FileKind getFileKind(PsiFile file) {
		return ZulDomUtil.isZKFile(file) ? FileKind.ZUL_FILE : null;
	}

	private static enum FileKind {
		ZUL_FILE {
			public String getSchemaUrl() {
				return ZulSchemaProvider.ZUL_PROJECT_SCHEMA_URL;
			}
		};

		private FileKind() {
		}

		public abstract String getSchemaUrl();
	}
}