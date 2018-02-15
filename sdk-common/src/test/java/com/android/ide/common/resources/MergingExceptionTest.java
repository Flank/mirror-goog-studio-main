/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.xml.sax.SAXParseException;

@SuppressWarnings({"ThrowableInstanceNeverThrown", "ThrowableResultOfMethodCallIgnored"})
public class MergingExceptionTest {

    private static final File file = new File("/some/random/path");

    @Test
    public void testGetMessage() {
        // Note: On Windows the root '/' is transformed to 'C:/', so we call getAbsoluteFile()
        String msgPath = file.getAbsolutePath();

        assertEquals("Error: My error message",
                     MergingException.withMessage("My error message").build().getMessage());
        assertEquals("Error: My error message",
                MergingException.wrapException(new Exception()).withMessage(
                        "Error: My error message").build().getMessage());
        assertEquals("Error: My error message",
                MergingException.withMessage("Error: My error message").build().getMessage());
        assertEquals(msgPath + ": Error: My error message",
                MergingException.wrapException(new Exception("My error message")).withFile(file)
                        .build().getMessage());
        MergingException.Builder builder2 = MergingException
                .wrapException(new Exception("My error message")).withFile(file)
                .withPosition(new SourcePosition(49, -1, -1));
        assertEquals(msgPath + ":50: Error: My error message",
                builder2.build().getMessage());
        MergingException.Builder builder1 = MergingException
                .wrapException(new Exception("My error message")).withFile(file)
                .withPosition(new SourcePosition(49, 3, -1));
        assertEquals(msgPath + ":50:4: Error: My error message",
                builder1.build().getMessage());
        MergingException.Builder builder = MergingException
                .wrapException(new Exception("My error message")).withFile(file)
                .withPosition(new SourcePosition(49, 3, -1));
        assertEquals(msgPath + ":50:4: Error: My error message",
                builder.build().getLocalizedMessage());
        assertEquals(msgPath + ": Error: My error message",
                MergingException.withMessage(msgPath + ": My error message").withFile(file)
                        .build().getMessage());
        assertEquals(msgPath + ": Error: My error message",
                MergingException.withMessage(msgPath + " My error message").withFile(file)
                        .build().getMessage());

        // end of string handling checks
        assertEquals(msgPath + ": Error: ",
                MergingException.withMessage(msgPath).withFile(file).build()
                        .getMessage());
        assertEquals(msgPath + ": Error: ",
                MergingException.withMessage(msgPath).withFile(file).build().getMessage());
        assertEquals(msgPath + ": Error: ",
                MergingException.withMessage(msgPath + ":").withFile(file).build().getMessage());
        assertEquals(msgPath + ": Error: ",
                MergingException.withMessage(msgPath + ": ").withFile(file).build().getMessage());
    }


    @Test
    public void testWrapSaxParseExceptionWithLocation() {
        SAXParseException saxParseException = new SAXParseException("message", "", "", 5, 7);
        List<Message> messages = MergingException
                .wrapException(saxParseException).withFile(file).build().getMessages();
        Message message = Iterables.getOnlyElement(messages);
        assertEquals("message.getKind()", Message.Kind.ERROR, message.getKind());
        assertEquals("message.getText()", "message", message.getText());
        assertTrue("message.getRawMessage()",
                message.getRawMessage().startsWith("org.xml.sax.SAXParseException"));
        assertEquals("message.getSourceFilePositions()",
                new SourceFilePosition(file, new SourcePosition(4, 6, -1)),
                Iterables.getOnlyElement(message.getSourceFilePositions()));
    }

    @Test
    public void testWrapSaxParseExceptionWithoutLocation() {
        SAXParseException saxParseException = new SAXParseException("message2", "", "", -1, -1);

        List<Message> messages = MergingException
                .wrapException(saxParseException).withFile(file).build().getMessages();
        Message message = Iterables.getOnlyElement(messages);
        assertEquals("message.getKind()", Message.Kind.ERROR, message.getKind());
        assertEquals("message.getText()", "message2", message.getText());
        assertTrue("message.getRawMessage()",
                message.getRawMessage().startsWith("org.xml.sax.SAXParseException"));
        assertEquals("message.getSourceFilePositions()",
                new SourceFilePosition(file, SourcePosition.UNKNOWN),
                Iterables.getOnlyElement(message.getSourceFilePositions()));
    }

    @Test
    public void testThrowIfNonEmpty() throws MergingException{
        MergingException.throwIfNonEmpty(ImmutableList.<Message>of());
        try {
            MergingException.throwIfNonEmpty(ImmutableList.<Message>of(
                    new Message(Message.Kind.ERROR, "Message", SourceFilePosition.UNKNOWN)));
            fail();
        } catch (MergingException e) {
            // ok
        }
    }


    @Test
    public void testMergingExceptionWithNullMessage() {
        MergingException exception = MergingException.wrapException(new IOException()).build();
        Message message = Iterables.getOnlyElement(exception.getMessages());
        assertNotNull(message.getText());
        assertNotNull(message.getRawMessage());
    }

    @Test
    public void testConsumerExceptionWithNullMessage() {
        MergingException exception = new MergeConsumer.ConsumerException(new IOException());
        Message message = Iterables.getOnlyElement(exception.getMessages());
        assertNotNull(message.getText());
        assertNotNull(message.getRawMessage());
    }
}
