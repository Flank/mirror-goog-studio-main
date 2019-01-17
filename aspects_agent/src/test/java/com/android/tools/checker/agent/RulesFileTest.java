package com.android.tools.checker.agent;

import static junit.framework.TestCase.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.Test;

public class RulesFileTest {
    @Test
    public void testBasic() throws IOException {
        final String content =
                " # Test comment\n"
                        + "# invalid()V=#test\n"
                        + "java.test.Test.method()V=#warn1\n"
                        + "\n"
                        + "# Another comment\n"
                        + "java.test.Test.method2()V=#warn2\n";

        File outputTest = File.createTempFile("test", ".txt");
        outputTest.deleteOnExit();
        Files.write(outputTest.toPath(), content.getBytes(), StandardOpenOption.CREATE);

        Map<String, String> result = RulesFile.parserRulesFile(outputTest.getAbsolutePath());
        assertEquals("#warn1", result.get("java.test.Test.method()V"));
        assertEquals("#warn2", result.get("java.test.Test.method2()V"));
        assertEquals(2, result.size());
    }
}
