package com.android.tools.checker.agent;

import static com.android.tools.checker.agent.RulesFile.RulesFileException;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.testutils.TestUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class RulesFileTest {

    @Test
    public void rulesWithMethodsAndAnnotations() throws IOException, RulesFileException {
        RulesFile rulesFile = parserRulesFile("default.json");

        Map<String, String> aspects = rulesFile.getAspects();
        assertEquals("#warn1", aspects.get("java.test.Test.method"));
        assertEquals("#warn2", aspects.get("java.test.Test.method2"));
        assertEquals("#warn3", aspects.get("@annotations.threading.Slow"));
        assertEquals("#warn4", aspects.get("@annotations.threading.Fast"));
        assertEquals("something.else#fail", aspects.get("@annotations.wow.Wow"));
        assertEquals(5, aspects.size());

        Map<String, String> annotationGroups = rulesFile.getAnnotationGroups();
        assertEquals("threading", annotationGroups.get("annotations.threading.Slow"));
        assertEquals("threading", annotationGroups.get("annotations.threading.Fast"));
        assertEquals("random", annotationGroups.get("annotations.wow.Wow"));
        assertEquals(3, annotationGroups.size());
    }

    @Test
    public void rulesOnlyWithAnnotations() throws IOException, RulesFileException {
        RulesFile rulesFile = parserRulesFile("only_annotations.json");

        Map<String, String> aspects = rulesFile.getAspects();
        assertEquals("#warn3", aspects.get("@annotations.threading.Slow"));
        assertEquals("#warn4", aspects.get("@annotations.threading.Fast"));
        assertEquals("something.else#fail", aspects.get("@annotations.wow.Wow"));
        assertEquals(3, aspects.size());

        Map<String, String> annotationGroups = rulesFile.getAnnotationGroups();
        assertEquals("threading", annotationGroups.get("annotations.threading.Slow"));
        assertEquals("threading", annotationGroups.get("annotations.threading.Fast"));
        assertEquals("random", annotationGroups.get("annotations.wow.Wow"));
        assertEquals(3, annotationGroups.size());
    }

    @Test
    public void annotationWithoutLeadingAt() throws IOException, RulesFileException {
        try {
            parserRulesFile("annotation_without_at.json");
            fail(); // RulesFileException is expected to be thrown.
        } catch (RulesFileException ignore) {
            // expected
        }
    }

    @Test
    public void annotationsWithoutGroup() throws IOException, RulesFileException {
        RulesFile rulesFile = parserRulesFile("annotations_no_group.json");

        Map<String, String> aspects = rulesFile.getAspects();
        assertEquals("#warn3", aspects.get("@annotations.threading.Slow"));
        assertEquals("#warn4", aspects.get("@annotations.threading.Fast"));
        assertEquals("something.else#fail", aspects.get("@annotations.wow.Wow"));
        assertEquals(3, aspects.size());

        // Although we process 3 annotations, only 2 of them have a group specified.
        Map<String, String> annotationGroups = rulesFile.getAnnotationGroups();
        assertEquals("threading", annotationGroups.get("annotations.threading.Slow"));
        assertEquals("random", annotationGroups.get("annotations.wow.Wow"));
        assertEquals(2, annotationGroups.size());
    }

    @Test
    public void rulesOnlyWithMethods() throws IOException, RulesFileException {
        RulesFile rulesFile = parserRulesFile("only_methods.json");

        Map<String, String> aspects = rulesFile.getAspects();
        assertEquals("#warn1", aspects.get("java.test.Test.method"));
        assertEquals("#warn2", aspects.get("java.test.Test.method2"));
        assertEquals(2, aspects.size());

        Map<String, String> annotationGroups = rulesFile.getAnnotationGroups();
        assertTrue(annotationGroups.isEmpty());
    }

    @Test
    public void emptyRules() throws IOException, RulesFileException {
        RulesFile rulesFile = parserRulesFile("empty.json");

        Map<String, String> aspects = rulesFile.getAspects();
        assertTrue(aspects.isEmpty());

        Map<String, String> annotationGroups = rulesFile.getAnnotationGroups();
        assertTrue(annotationGroups.isEmpty());
    }

    @Test
    public void malformedRulesFile() throws IOException {
        try {
            parserRulesFile("malformed_file.json");
            fail(); // RulesFileException is expected to be thrown.
        } catch (RulesFileException ignore) {
            // expected
        }
    }

    @Test
    public void noNameField() throws IOException {
        try {
            parserRulesFile("no_name_field.json");
            fail(); // RulesFileException is expected to be thrown.
        } catch (RulesFileException ignore) {
            // expected
        }
    }

    @Test
    public void methodNoAspect() throws IOException {
        try {
            parserRulesFile("no_aspect_field.json");
            fail(); // RulesFileException is expected to be thrown.
        } catch (RulesFileException ignore) {
            // expected
        }
    }

    @Test
    public void annotationNoAspect() throws IOException, RulesFileException {
        RulesFile rulesFile = parserRulesFile("annotation_no_aspect_field.json");
        Map<String, String> aspects = rulesFile.getAspects();
        assertTrue(aspects.isEmpty());

        Map<String, String> annotationGroups = rulesFile.getAnnotationGroups();
        assertEquals(1, annotationGroups.size());
        assertEquals("random", annotationGroups.get("com.example.MyAnnotation"));
    }

    @Test
    public void annotationNoAspectNoGroup() throws IOException {
        try {
            parserRulesFile("annotation_no_aspect_field_no_group.json");
            fail(); // RulesFileException is expected to be thrown.
        } catch (RulesFileException ignore) {
            // expected
        }
    }

    private static RulesFile parserRulesFile(String rulesFileName)
            throws FileNotFoundException, RulesFileException {
        String testDataPath = "tools/base/aspects_agent/testData/rules/";
        Path rules =
                TestUtils.resolveWorkspacePath(String.format("%s%s", testDataPath, rulesFileName));
        RulesFile rulesFile = new RulesFile(rules.toString());
        rulesFile.parseRulesFile(Function.identity(), Function.identity());
        return rulesFile;
    }
}
