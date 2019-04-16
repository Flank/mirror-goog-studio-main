/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.deployer.devices.shell.interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.tools.deployer.devices.shell.ShellCommand;
import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InterpreterTest {
    private ByteArrayInputStream inputStream;
    private ByteArrayOutputStream outputStream;
    private Map<String, ShellCommand> commands;
    private ShellEnv env;

    @Before
    public void before() throws IOException {
        inputStream = new ByteArrayInputStream(new byte[] {});
        outputStream = new ByteArrayOutputStream(1024);
        commands = new HashMap<>();
        commands.put("echo", new EchoCommand());
        commands.put("cat", new CatCommand());
        env =
                new ShellEnv(
                        new FakeDeviceLibrary().build(FakeDeviceLibrary.DeviceId.API_28),
                        commands,
                        inputStream,
                        outputStream);
    }

    @After
    public void after() throws IOException {
        inputStream.close();
        outputStream.close();
    }

    @Test
    public void echoTest() throws IOException {
        assertTrue(Parser.parse("echo foo").execute(env).success);
        assertEquals(String.format("foo%s", System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void echoSemicolonTest() throws IOException {
        assertTrue(Parser.parse("echo foo;").execute(env).success);
        assertEquals(String.format("foo%s", System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void quotedCommandTest() throws IOException {
        assertTrue(Parser.parse("\"echo\" foo").execute(env).success);
        assertEquals(String.format("foo%s", System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void pipeTest() throws IOException {
        assertTrue(Parser.parse("echo foo | cat").execute(env).success);
        assertEquals(String.format("foo%s", System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void pipeWithFileTest() throws IOException {
        final String content = "asdf";
        env.getDevice().writeFile("/bar.txt", content.getBytes(Charsets.UTF_8), "420");
        assertTrue(Parser.parse("echo foo | cat /bar.txt").execute(env).success);
        assertEquals(content, env.readStringFromPipe());
    }

    @Test
    public void pipeWithQuotedFileNameTest() throws IOException {
        final String content = "asdf";
        env.getDevice().writeFile("/bar.txt", content.getBytes(Charsets.UTF_8), "420");
        assertTrue(Parser.parse("echo foo | cat '/bar.txt'").execute(env).success);
        assertEquals(content, env.readStringFromPipe());
    }

    @Test
    public void pipeWithDoubleQuotedFileNameTest() throws IOException {
        final String content = "asdf";
        env.getDevice().writeFile("/bar.txt", content.getBytes(Charsets.UTF_8), "420");
        assertTrue(Parser.parse("echo foo | cat \"/bar.txt\"").execute(env).success);
        assertEquals(content, env.readStringFromPipe());
    }

    @Test
    public void varTest() throws IOException {
        assertTrue(Parser.parse("foo= asdf; echo $foo").execute(env).success);
        assertEquals(String.format("asdf%s", System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void ifTest() throws IOException {
        assertTrue(Parser.parse("if [[ a == a* ]]; then echo foo; fi").execute(env).success);
        assertEquals(String.format("foo%s", System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void forTest() throws IOException {
        assertTrue(Parser.parse("for pid in 123 456; do echo $pid; done").execute(env).success);
        assertEquals(String.format("123%s456%s", System.lineSeparator(), System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void compoundForIfTest() throws IOException {
        assertTrue(
                Parser.parse(
                                "for path in /foo /bar; do "
                                        + "    if [[ $path == /foo* ]]; then "
                                        + "        echo $path; "
                                        + "    fi; "
                                        + "done")
                        .execute(env)
                        .success);
        assertEquals(String.format("/foo%s", System.lineSeparator()), env.readStringFromPipe());
    }

    @Test
    public void invalidCommand() {
        assertFalse(Parser.parse("aaaa foo").execute(env).success);
    }

    @Test(expected = RuntimeException.class)
    public void noDanglingOperatorAnd() {
        Parser.parse("echo foo &&");
    }

    @Test(expected = RuntimeException.class)
    public void noDanglingOperatorPipe() {
        Parser.parse("echo foo |");
    }

    private static class EchoCommand extends ShellCommand {
        @Override
        public boolean execute(
                FakeDevice device, String[] args, InputStream stdin, PrintStream stdout) {
            stdout.println(args[0]); // echo prints a newline after the param.
            return true;
        }

        @Override
        public String getExecutable() {
            return "echo";
        }
    }

    private static class CatCommand extends ShellCommand {
        @Override
        public boolean execute(
                FakeDevice device, String[] args, InputStream stdin, PrintStream stdout)
                throws IOException {
            // Always read all from stdin.
            int available = stdin.available();
            byte[] buffer = new byte[0];
            if (available > 0) {
                buffer = new byte[available];
                int read = 0;
                int totalRead = 0;
                while (totalRead < available && read != -1) {
                    read = stdin.read(buffer, read, buffer.length - read);
                    totalRead += read;
                }
            }

            // If an argument was supplied, print the contents of that file instead and ignore stdin.
            if (args.length > 0) {
                buffer = device.readFile(args[0]);
                if (buffer == null) {
                    return false;
                }
            }
            stdout.print(new String(buffer, Charsets.UTF_8));
            return true;
        }

        @Override
        public String getExecutable() {
            return "cat";
        }
    }
}
