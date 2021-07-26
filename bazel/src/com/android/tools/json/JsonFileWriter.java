/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

/** Utility for writing an object in JSON format. */
public final class JsonFileWriter {

    private JsonFileWriter() {}

    /** Writes the object to the given file in JSON format. */
    public static void write(String filePath, Object result) throws Exception {
        getMapper().writerWithDefaultPrettyPrinter().writeValue(new File(filePath), result);
    }

    /** Writes the object to standard output in JSON format. */
    public static void print(Object result) throws JsonProcessingException {
        System.out.println(getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    private static ObjectMapper getMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }
}
