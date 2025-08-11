/* ZKNews.java

	Purpose:

	Description:

	History:
		Thu Sep 09 16:35:58 CST 2021, Created by katherine

Copyright (C) 2021 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.newsNotification;

import java.io.File;
import java.io.IOException;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.io.FileUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.zkoss.zkidea.project.*;

/**
 * ZK News notification service that fetches and displays news from the ZK website.
 * 
 * <p>This class implements {@link ProjectActivity} instead of the deprecated 
 * {@code StartupActivity} to provide non-blocking asynchronous execution during 
 * project startup. This design choice is crucial for maintaining IDE responsiveness
 * when the ZK website has slow response times or becomes unreachable.</p>
 * 
 * @since 0.1.23
 */
public class ZKNews implements ProjectActivity {
	public static final String ZK_WEBSITE_URL = "https://www.zkoss.org?ide=in";
	public static final String ZK_NEWS_FETCH_URL = ZK_WEBSITE_URL + "&fetch=true";
	private static final Logger logger = Logger.getInstance(ZKNews.class);

	/**
	 * Executes the news fetching process asynchronously when a project starts.
	 * 
	 * <p>This method is called by the IntelliJ Platform on a background thread,
	 * ensuring that network operations don't block the IDE startup process.
	 * The method safely handles exceptions to prevent startup failures.</p>
	 * 
	 * @param project the project being opened (not null)
	 * @param continuation the coroutine continuation (required by ProjectActivity interface)
	 * @return null (no return value needed for this startup activity)
	 */
	@Override
	public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
		if (!project.isDefault()) {
			try {
				newsNotificationPopup(project);
			} catch (Exception e) {
				logger.debug("Failed to fetch ZK news", e);
			}
		}
		return null;
	}

	/**
	 * Handles the news notification display logic with local caching.
	 * 
	 * <p>This method compares fetched news with locally cached content to avoid
	 * showing duplicate notifications. Only displays notifications when new
	 * content is available from the ZK website.</p>
	 * 
	 * @param project the current project for notification context
	 * @throws IOException if file operations or network requests fail
	 */
	private void newsNotificationPopup(Project project) throws IOException {
		File zkNewsFile = new File(ZKPathManager.getPluginTempPath() + File.separator + "zkNews.txt");
		if (!zkNewsFile.exists())
			zkNewsFile.createNewFile();
		String newsCache  = FileUtil.loadFile(zkNewsFile);
		String news = newsLoader();
		if(!news.equals(newsCache)) {
			FileUtil.writeToFile(zkNewsFile, news);
			NotificationGroupManager.getInstance().getNotificationGroup("news notification")
					.createNotification(news, NotificationType.INFORMATION)
					.addAction(NotificationAction.createSimpleExpiring("Visit zkoss.org for detail.",
							() -> BrowserUtil.browse(ZK_WEBSITE_URL + "&read=more#news-sec")))
					.notify(project);
		}
	}

	/**
	 * Fetches the latest news content from the ZK website.
	 * 
	 * <p>Uses JSoup to scrape news content with a 5-second timeout to prevent
	 * hanging when the website is slow or unreachable. The timeout ensures
	 * that this background operation doesn't impact IDE responsiveness.</p>
	 * 
	 * @return the formatted news content as a string
	 * @throws IOException if the network request fails or times out
	 */
	private String newsLoader() throws IOException {
		Document doc = Jsoup.connect(ZK_NEWS_FETCH_URL).timeout(5000).get();
		Element element = doc.select("#row-news-inner-box .desc span").get(0);
		String tagName = element.tagName();
		String news = element.toString().replace("<" + tagName + ">","").replace("</" + tagName + ">","");
		if (!news.endsWith("!") && !news.endsWith("."))
			news = news.concat(".");
		return news;
	}
}