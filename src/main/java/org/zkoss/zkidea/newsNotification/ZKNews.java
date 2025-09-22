/* ZKNews.java

	Purpose:
		
	Description:
		
	History:
		Thu Sep 09 16:35:58 CST 2021, Created by katherine

Copyright (C) 2021 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.newsNotification;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.zkoss.zkidea.project.*;

/**
 * Manages ZK Framework news notifications within the IDE.
 * Fetches and displays ZK framework news, updates, and announcements
 * to keep developers informed about the latest ZK developments.
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
	private static final int INTERVAL_DAYS = 7;
	private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;

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
	 * Handles the news notification display logic with Properties-based caching.
	 *
	 * <p>Shows popup when:</p>
	 * <ul>
	 *   <li>No cached news exists (first run)</li>
	 *   <li>Latest news content differs from cached content</li>
	 *   <li>Same content but over 7 days since last shown</li>
	 * </ul>
	 *
	 * @param project the current project for notification context
	 * @throws IOException if file operations or network requests fail
	 */
	private void newsNotificationPopup(Project project) throws IOException {
		Path cacheDir = Paths.get(ZKPathManager.getPluginTempPath());
		Files.createDirectories(cacheDir);

		File zkNewsFile = new File(cacheDir.toFile(), "zkNews.properties");

		String currentNews = newsLoader();
		long currentTime = System.currentTimeMillis();

		boolean shouldShow = shouldShowNotification(zkNewsFile, currentNews, currentTime);

		if (shouldShow) {
			updateCache(zkNewsFile, currentNews, currentTime);
			NotificationGroupManager.getInstance().getNotificationGroup("news notification")
					.createNotification(currentNews, NotificationType.INFORMATION)
					.addAction(NotificationAction.createSimple("Visit zkoss.org for detail.",
							() -> BrowserUtil.browse(ZK_WEBSITE_URL + "&read=more#news-sec")))
					.notify(project);
		}
	}

	/**
	 * Determines whether a notification should be shown based on cache state and news content.
	 *
	 * @param zkNewsFile
	 * @param currentNews the latest news content
	 * @param currentTime the current timestamp
	 * @return true if notification should be shown
	 */
	private boolean shouldShowNotification(File zkNewsFile, String currentNews, long currentTime) {
		Properties cache = loadCache(zkNewsFile);
		String cachedNews = cache.getProperty("content", "");
		String lastShownStr = cache.getProperty("lastShown", "0");

		if (cache.isEmpty() || cachedNews.isEmpty()) {
			return true;
		}

		if (!currentNews.equals(cachedNews)) {
			return true;
		}

		long lastShown = Long.parseLong(lastShownStr);
		long daysSinceLastShown = (currentTime - lastShown) / MILLIS_PER_DAY;

		return daysSinceLastShown >= INTERVAL_DAYS;
	}

	/**
	 * Loads cache properties from the specified file.
	 * Returns empty properties if file doesn't exist or can't be read.
	 *
	 * @param cacheFile the cache file to read from
	 * @return Properties object with cached data
	 */
	private Properties loadCache(File cacheFile) {
		Properties cache = new Properties();
		if (cacheFile.exists()) {
			try (FileInputStream fis = new FileInputStream(cacheFile)) {
				cache.load(fis);
			} catch (IOException e) {
				logger.debug("Failed to load cache file", e);
			}
		}
		return cache;
	}

	/**
	 * Updates cache with new content and timestamp.
	 *
	 * @param cacheFile the cache file to update
	 * @param content the news content to cache
	 * @param timestamp the timestamp when shown
	 */
	private void updateCache(File cacheFile, String content, long timestamp) {
		Properties cache = new Properties();
		cache.setProperty("content", content);
		cache.setProperty("lastShown", String.valueOf(timestamp));

		try {
			Files.createDirectories(cacheFile.getParentFile().toPath());
		} catch (IOException e) {
			logger.debug("Failed to create cache directory", e);
			return;
		}

		try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
			cache.store(fos, "ZK News Cache - lastShown timestamp in epoch milliseconds");
		} catch (IOException e) {
			logger.debug("Failed to update cache file", e);
		}
	}

	/**
	 * Fetches the latest news content from the ZK website.
	 *
	 * <p>Uses JSoup to scrape news content with a 5-second timeout to prevent
	 * hanging when the website is slow or unreachable. The timeout ensures
	 * that this background operation doesn't impact IDE responsiveness.</p>
	 *
	 * @return the formatted news text
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