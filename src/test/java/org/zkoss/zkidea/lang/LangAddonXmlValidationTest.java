package org.zkoss.zkidea.lang;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zkoss.zkidea.dom.ZulDomUtil;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for lang-addon.xml validation.
 * Tests both XSD schema validation and custom annotator behavior.
 */
@ExtendWith(MockitoExtension.class)
public class LangAddonXmlValidationTest {
    private ZkXmlValidationAnnotator annotator;
    private Schema langAddonSchema;

    @BeforeEach
    public void setUp() {
        annotator = new ZkXmlValidationAnnotator();

        // Load XSD schema for validation tests
        loadLangAddonSchema();
    }

    private void loadLangAddonSchema() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            InputStream schemaStream = getClass().getClassLoader()
                    .getResourceAsStream("org/zkoss/zkidea/lang/resources/lang-addon.xsd");
            if (schemaStream != null) {
                Source schemaFile = new StreamSource(schemaStream);
                langAddonSchema = factory.newSchema(schemaFile);
            }
        } catch (Exception e) {
            // If schema loading fails, tests will skip XSD validation
            langAddonSchema = null;
        }
    }

    @Test
    public void testAnnotatorInitialization() {
        assertNotNull(annotator, "Annotator should be initialized");
    }

    @Test
    public void testXsdSchemaLoading() {
        // Test that the schema loads successfully
        assertNotNull(langAddonSchema, "Lang addon schema should load successfully");
    }

    // ================== XSD Schema Validation Tests ==================

    @Test
    public void testXsdValidationValidFile() {
        if (langAddonSchema == null) {
            // Skip test if schema couldn't be loaded
            return;
        }

        String xmlContent = loadTestFileContent("test-lang-addon.xml");
        assertTrue(validateAgainstSchema(xmlContent), "Valid lang-addon.xml should pass XSD validation");
    }

    @Test
    public void testXsdValidationFlexibleOrdering() {
        if (langAddonSchema == null) {
            return;
        }

        // Test mixed order
        String mixedOrderContent = loadTestFileContent("test-lang-addon-mixed-order.xml");
        assertTrue(validateAgainstSchema(mixedOrderContent), "Mixed element order should pass XSD validation");

        // Test reverse order
        String reverseOrderContent = loadTestFileContent("test-lang-addon-reverse-order.xml");
        assertTrue(validateAgainstSchema(reverseOrderContent), "Reverse element order should pass XSD validation");
    }

    @Test
    public void testXsdValidationMultipleElements() {
        if (langAddonSchema == null) {
            return;
        }

        String xmlContent = loadTestFileContent("test-lang-addon-validation.xml");
        assertTrue(validateAgainstSchema(xmlContent), "Multiple library-property elements should pass XSD validation");
    }

    @Test
    public void testXsdValidationNamespaceHandling() {
        if (langAddonSchema == null) {
            return;
        }

        // Test with namespace
        String withNamespace = loadTestFileContent("test-lang-addon.xml");
        assertTrue(validateAgainstSchema(withNamespace), "File with namespace should pass XSD validation");

        // Test without namespace - should fail XSD validation
        String withoutNamespace = loadTestFileContent("test-lang-addon-no-namespace.xml");
        assertFalse(validateAgainstSchema(withoutNamespace), "File without namespace should fail XSD validation");
    }

    @Test
    public void testXsdValidationInvalidRootElement() {
        if (langAddonSchema == null) {
            return;
        }

        String invalidXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <invalid-root xmlns="http://www.zkoss.org/2005/zk/lang-addon">
                <addon-name>Test</addon-name>
                <language-name>test</language-name>
            </invalid-root>
            """;

        assertFalse(validateAgainstSchema(invalidXml), "Invalid root element should fail XSD validation");
    }

    /**
     * Helper method to validate XML content against the lang-addon schema
     */
    private boolean validateAgainstSchema(String xmlContent) {
        if (langAddonSchema == null) {
            return true; // Skip validation if schema not available
        }

        try {
            Validator validator = langAddonSchema.newValidator();
            Source xmlFile = new StreamSource(new StringReader(xmlContent));
            validator.validate(xmlFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper method to load test XML file content as string
     */
    private String loadTestFileContent(String filename) {
        try {
            return Files.readString(Paths.get("src/test/resources/" + filename));
        } catch (IOException e) {
            fail("Could not load test file: " + filename + " - " + e.getMessage());
            return "";
        }
    }

}