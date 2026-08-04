package com.example.springbootjar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot (jar-packaging) application, present so this sample is a faithful
 * {@code packaging=jar} Spring Boot project.
 *
 * <p><b>The ZUL Layout Preview does not start this application.</b> The plugin renders the
 * pages under {@code src/main/resources/web/} headlessly through its {@code zk-preview-launcher}
 * helper JVM, using the module's ZK jars, its resource roots (on the launcher classpath), and the
 * resolved docroot ({@code src/main/resources/web}). This class only exists to make the module a
 * real Spring Boot jar project for the manual runIde check; it is never invoked by the preview.
 */
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
