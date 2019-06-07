package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Parses the agent rules file (see README for format and more details) into the following objects:
 *
 * <p>1) {@link #aspects}: maps the methods/annotations being intercepted to the method that should
 * be called before calling the intercepted one.
 *
 * <p>2) {@link #annotationGroups}: maps the annotations specified in the rules file to its group
 * name.
 */
public class RulesFile {

    @NonNull private final String filePath;

    @NonNull private Map<String, String> aspects = new HashMap<>();

    @NonNull private Map<String, String> annotationGroups = new HashMap<>();

    RulesFile(@NonNull String filePath) {
        this.filePath = filePath;
    }

    void parseRulesFile(
            @NonNull Function<String, String> aspectKeyProcessor,
            @NonNull Function<String, String> aspectValueProcessor)
            throws FileNotFoundException, RulesFileException {
        JsonParser parser = new JsonParser();
        JsonObject root;
        try {
            root = parser.parse(new FileReader(filePath)).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new RulesFileException("Error while parsing the rules JSON file.", e);
        }
        processElementList(root, "methods", false, aspectKeyProcessor, aspectValueProcessor);
        processElementList(root, "annotations", true, aspectKeyProcessor, aspectValueProcessor);
    }

    private void processElementList(
            JsonObject root,
            String elementName,
            boolean isAnnotation,
            @NonNull Function<String, String> aspectKeyProcessor,
            @NonNull Function<String, String> aspectValueProcessor)
            throws RulesFileException {
        if (!root.has(elementName)) {
            return;
        }
        JsonArray list = root.get(elementName).getAsJsonArray();
        for (JsonElement element : list) {
            JsonObject object = element.getAsJsonObject();

            String name = getElementAsString("name", object);
            if (isAnnotation && !name.startsWith("@")) {
                throw new RulesFileException(
                        "\"name\" field of annotation rules must start with @.");
            }

            // Some annotations can be defined without aspects, for example to specify the
            // annotation group to avoid conflicts. In this case, the group attribute is mandatory.
            if (isAnnotation && !object.has("aspect")) {
                annotationGroups.put(name.substring(1), getElementAsString("group", object));
                continue;
            }

            String aspect = getElementAsString("aspect", object);
            aspects.put(aspectKeyProcessor.apply(name), aspectValueProcessor.apply(aspect));

            if (isAnnotation && object.has("group")) {
                // Store the annotation group without the leading @, since we don't need to
                // distinguish the annotations from methods here and it's easier to convert the
                // annotation into a class name later.
                annotationGroups.put(name.substring(1), object.get("group").getAsString());
            }
        }
    }

    @NonNull
    private static String getElementAsString(
            @NonNull String elementName, @NonNull JsonObject parent) throws RulesFileException {
        JsonElement element = parent.get(elementName);
        if (element == null) {
            throw new RulesFileException(
                    String.format("Aspect missing mandatory field \"%s\".", elementName));
        }
        return element.getAsString();
    }

    @NonNull
    public Map<String, String> getAspects() {
        return aspects;
    }

    @NonNull
    public Map<String, String> getAnnotationGroups() {
        return annotationGroups;
    }

    public static class RulesFileException extends Exception {

        private static final String BASE_MSG = "Error while reading the aspects agent rules file: ";

        public RulesFileException(String msg) {
            super(BASE_MSG + msg);
        }

        public RulesFileException(String msg, Throwable cause) {
            super(BASE_MSG + msg, cause);
        }
    }
}
