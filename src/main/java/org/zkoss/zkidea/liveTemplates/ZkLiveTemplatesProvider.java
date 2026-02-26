package org.zkoss.zkidea.liveTemplates;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;

public class ZkLiveTemplatesProvider implements DefaultLiveTemplatesProvider {

    @Override
    public String[] getDefaultLiveTemplateFiles() {
        return new String[]{"/liveTemplates/ZK"};
    }

    @Override
    public String[] getHiddenLiveTemplateFiles() {
        return new String[0];
    }
}
