/* ZKNews.java

	Purpose:

	Description:

	History:
		Thu Sep 09 16:35:58 CST 2021, Created by katherine

Copyright (C) 2021 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.newsNotification;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.zkoss.zkidea.project.ZKPathManager;
import java.io.File;
import java.io.IOException;

public class ZKNews implements StartupActivity {
	public static final String ZK_WEBSITE_URL = "http://www.zkoss.org";

	@Override
	public void runActivity(@NotNull Project project) {
		try {
			if (!project.isDefault()) {
				newsNotificationPopup(project);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void newsNotificationPopup(Project project) throws IOException {
		File zkNewsFile = new File(ZKPathManager.getPluginTempPath() + File.separator + "zkNews.txt");
		if (!zkNewsFile.exists())
			zkNewsFile.createNewFile();
		String news = newsLoader(ZK_WEBSITE_URL);
		String newsCache  = FileUtil.loadFile(zkNewsFile);
		if(!news.equals(newsCache)) {
			FileUtil.writeToFile(zkNewsFile, news);
			NotificationGroupManager.getInstance().getNotificationGroup("news notification")
					.createNotification(news + " Visit <a href=\"" + ZK_WEBSITE_URL + "\">zkoss.org</a> for detail.", NotificationType.INFORMATION)
					.setListener(new NotificationListener.UrlOpeningListener(true))
					.notify(project);
		}
	}

	private String newsLoader(String url) throws IOException {
		Document doc = Jsoup.connect(url).get();
		Element element = doc.select("#row-news-inner-box .desc span").get(0);
		String tagName = element.tagName();
		String news = element.toString().replace("<" + tagName + ">","").replace("</" + tagName + ">","");
		if (!news.endsWith("!") && !news.endsWith("."))
			news.concat(".");
		return news;
	}
}