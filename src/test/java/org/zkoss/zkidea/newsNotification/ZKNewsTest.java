/* ZKNewsTest.java

	Purpose:
		
	Description:
		
	History:
		Created for testing ZKNews functionality

Copyright (C) 2021 Potix Corporation. All Rights Reserved.
*/
package org.zkoss.zkidea.newsNotification;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.zkoss.zkidea.project.ZKPathManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Properties;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZKNews notification functionality.
 * Tests the ZK news fetching, caching, and notification display behavior
 * using JUnit 5 and Mockito for mocking external dependencies.
 */
@ExtendWith(MockitoExtension.class)
public class ZKNewsTest {

    private ZKNews zkNews;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        zkNews = new ZKNews();
    }

    @Test
    public void testNewsLoaderWithActualWebsite() throws Exception {
        // This test makes an actual HTTP request to the ZK website
        // It may fail if the website is down or the network is unavailable

        // Act
        Method newsLoaderMethod = ZKNews.class.getDeclaredMethod("newsLoader");
        newsLoaderMethod.setAccessible(true);
        String result = (String) newsLoaderMethod.invoke(zkNews);

        // Assert
        assertNotNull(result, "News content should not be null");
        assertFalse(result.trim().isEmpty(), "News content should not be empty");
        assertTrue(result.endsWith(".") || result.endsWith("!"), "News content should end with proper punctuation");

        // Basic content validation - should not contain HTML tags
        assertFalse(result.contains("<"), "News content should not contain HTML tags");
        assertFalse(result.contains(">"), "News content should not contain HTML tags");
    }

    @Test
    public void testLoadCacheFromNonexistentFile() throws Exception {
        // Arrange
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.properties");

        // Act
        Method loadCacheMethod = ZKNews.class.getDeclaredMethod("loadCache", File.class);
        loadCacheMethod.setAccessible(true);
        Properties result = (Properties) loadCacheMethod.invoke(zkNews, nonExistentFile);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testUpdateCacheCreatesPropertiesFile() throws Exception {
        // Arrange
        File cacheFile = new File(tempDir.toFile(), "zkNews.properties");
        String testContent = "Test news content";
        long testTimestamp = System.currentTimeMillis();

        // Act
        Method updateCacheMethod = ZKNews.class.getDeclaredMethod("updateCache", File.class, String.class, long.class);
        updateCacheMethod.setAccessible(true);
        updateCacheMethod.invoke(zkNews, cacheFile, testContent, testTimestamp);

        // Assert
        assertTrue(cacheFile.exists());

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(cacheFile)) {
            props.load(fis);
        }

        assertEquals(testContent, props.getProperty("content"));
        assertEquals(String.valueOf(testTimestamp), props.getProperty("lastShown"));
    }

    @Test
    public void testUpdateCacheCreatesBackup() throws Exception {
        // Arrange
        File cacheFile = new File(tempDir.toFile(), "zkNews.properties");
        String originalContent = "Original news";
        long originalTimestamp = System.currentTimeMillis() - 1000;

        // Create initial cache file
        Method updateCacheMethod = ZKNews.class.getDeclaredMethod("updateCache", File.class, String.class, long.class);
        updateCacheMethod.setAccessible(true);
        updateCacheMethod.invoke(zkNews, cacheFile, originalContent, originalTimestamp);

        // Act - Update with new content
        String newContent = "Updated news";
        long newTimestamp = System.currentTimeMillis();
        updateCacheMethod.invoke(zkNews, cacheFile, newContent, newTimestamp);

        // Assert
        File backupFile = new File(cacheFile.getAbsolutePath() + ".bak");
        assertTrue(backupFile.exists());

        // Verify backup contains original content
        Properties backupProps = new Properties();
        try (FileInputStream fis = new FileInputStream(backupFile)) {
            backupProps.load(fis);
        }
        assertEquals(originalContent, backupProps.getProperty("content"));

        // Verify main file has new content
        Properties currentProps = new Properties();
        try (FileInputStream fis = new FileInputStream(cacheFile)) {
            currentProps.load(fis);
        }
        assertEquals(newContent, currentProps.getProperty("content"));
    }

    @Test
    public void testLoadCacheFromExistingFile() throws Exception {
        // Arrange
        File cacheFile = new File(tempDir.toFile(), "zkNews.properties");
        String testContent = "Cached news content";
        long testTimestamp = System.currentTimeMillis();

        // Create cache file first
        Method updateCacheMethod = ZKNews.class.getDeclaredMethod("updateCache", File.class, String.class, long.class);
        updateCacheMethod.setAccessible(true);
        updateCacheMethod.invoke(zkNews, cacheFile, testContent, testTimestamp);

        // Act
        Method loadCacheMethod = ZKNews.class.getDeclaredMethod("loadCache", File.class);
        loadCacheMethod.setAccessible(true);
        Properties result = (Properties) loadCacheMethod.invoke(zkNews, cacheFile);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(testContent, result.getProperty("content"));
        assertEquals(String.valueOf(testTimestamp), result.getProperty("lastShown"));
    }


}