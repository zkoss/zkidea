package org.zkoss.zkidea.newsNotification;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.lang.reflect.Method;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ZKNewsTest {

    private ZKNews zkNews;

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


}