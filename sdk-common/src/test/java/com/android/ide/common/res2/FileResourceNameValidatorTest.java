/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.res2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FileResourceNameValidatorTest {

    @Parameterized.Parameters(name = "file={0}, resourceType={1} gives error {2}")
    public static Collection<Object[]> expected() {
        final String IS_NOT_A_VALID_ETC =
                " is not a valid file-based resource name character: File-based resource names "
                        + "must contain only lowercase a-z, 0-9, or underscore";
        final String THE_FILE_NAME_MUST_END_WITH_XML_OR_PNG =
                "The file name must end with .xml or .png";
        final String THE_FILE_NAME_MUST_END_WITH_XML_OR_TTF =
                "The file name must end with .xml, .ttf, .ttc or .otf";
        return Arrays.asList(
                new Object[][] {
                    //{ resourceName, resourceType, sourceFile, expectedException }
                    {"", ResourceFolderType.ANIMATOR, "Resource must have a name"},
                    {"foo.xml", ResourceFolderType.DRAWABLE, null},
                    {"foo.XML", ResourceFolderType.DRAWABLE, null},
                    {"foo.xML", ResourceFolderType.DRAWABLE, null},
                    {"foo.png", ResourceFolderType.DRAWABLE, null},
                    {"foo.9.png", ResourceFolderType.DRAWABLE, null},
                    {"foo.gif", ResourceFolderType.DRAWABLE, null},
                    {"foo.jpg", ResourceFolderType.DRAWABLE, null},
                    {"foo.jpeg", ResourceFolderType.DRAWABLE, null},
                    {"foo.bmp", ResourceFolderType.DRAWABLE, null},
                    {"foo.webp", ResourceFolderType.DRAWABLE, null},
                    {"foo.other.png", ResourceFolderType.DRAWABLE, "'.'" + IS_NOT_A_VALID_ETC},
                    {"foo.xml", ResourceFolderType.XML, null},
                    {"foo.xsd", ResourceFolderType.XML, null},
                    {"foo.xml", ResourceFolderType.FONT, null},
                    {"foo.ttf", ResourceFolderType.FONT, null},
                    {"foo.ttc", ResourceFolderType.FONT, null},
                    {"foo.otf", ResourceFolderType.FONT, null},
                    {"foo.png", ResourceFolderType.FONT, THE_FILE_NAME_MUST_END_WITH_XML_OR_TTF},
                    {"_foo.png", ResourceFolderType.DRAWABLE, null},
                    {"foo.png", ResourceFolderType.XML, "The file name must end with .xml"},
                    {"foo.8.xml", ResourceFolderType.DRAWABLE, "'.'" + IS_NOT_A_VALID_ETC},
                    {"foo", ResourceFolderType.DRAWABLE, THE_FILE_NAME_MUST_END_WITH_XML_OR_PNG},
                    {"foo.txt", ResourceFolderType.RAW, null},
                    {"foo", ResourceFolderType.RAW, null},
                    {"foo", ResourceFolderType.RAW, null},
                    {
                        "foo.txt",
                        ResourceFolderType.DRAWABLE,
                        THE_FILE_NAME_MUST_END_WITH_XML_OR_PNG
                    },
                    {
                        "1foo.png",
                        ResourceFolderType.DRAWABLE,
                        "The resource name must start with a letter"
                    },
                    {"Foo.png", ResourceFolderType.DRAWABLE, "'F'" + IS_NOT_A_VALID_ETC},
                    {"foo$.png", ResourceFolderType.DRAWABLE, "'$'" + IS_NOT_A_VALID_ETC},
                    {"bAr.png", ResourceFolderType.DRAWABLE, "'A'" + IS_NOT_A_VALID_ETC},
                    {
                        "enum.png",
                        ResourceFolderType.DRAWABLE,
                        "enum is not a valid resource name (reserved Java keyword)"
                    },
                });
    }

    @Parameterized.Parameter(value = 0)
    public String mSourceFileName;

    @Parameterized.Parameter(value = 1)
    public ResourceFolderType mResourceFolderType;

    @Parameterized.Parameter(value = 2)
    public String mExpectedErrorMessage;


    @Test
    public void validate() {
        String errorMessage = null;
        File file = new File(mSourceFileName);
        try {
            FileResourceNameValidator.validate(file, mResourceFolderType);
        } catch (MergingException e) {
            errorMessage = e.getMessage();
        }
        assertErrorMessageCorrect(mExpectedErrorMessage, errorMessage, file);
    }

    static void assertErrorMessageCorrect(
            @Nullable String expected, @Nullable String actual, @Nullable File file) {
        if (expected == null) {
            assertNull("Was not expecting error ", actual);
        } else {
            assertNotNull("Was expecting error " + expected + " but passed", actual);
            if (file == null) {
                assertEquals("Error: " + expected, actual);
            } else {
                assertEquals(file.getAbsolutePath() + ": Error: " + expected, actual);
            }
        }
    }

    static void assertErrorMessageCorrect(@Nullable String expected, @Nullable String actual) {
        if (expected == null) {
            assertNull("Was not expecting error ", actual);
        } else {
            assertNotNull("Was expecting error " + expected + " but passed", actual);
            assertEquals(expected, actual);
        }
    }
}
