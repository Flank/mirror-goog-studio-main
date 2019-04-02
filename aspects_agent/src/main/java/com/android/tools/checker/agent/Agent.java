package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import com.android.tools.checker.Assertions;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

@SuppressWarnings("unused") // Used via -javaagent path
public class Agent {
    private static final Logger LOGGER = Logger.getLogger(Agent.class.getName());

    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        agentmain(agentArgs, inst);
    }

    @NonNull
    private static String shortcutProcessor(@NonNull String value) {
        value = value.trim();
        if (value.startsWith("#")) {
            return Assertions.class.getName() + value;
        }

        return value;
    }

    public static void agentmain(String agentArgs, Instrumentation inst) throws IOException {
        /*
         * agentArgs should be in one of the formats below:
         *
         * 1) Two strings separated by a semicolon character (;). In this case, the first string is
         *    the rules file path, while the second one is the path to the baseline file.
         * 2) A single string without a semicolon character representing the path to the rules file.
         *
         * The rules file is simply a list of key=value pairs defining the aspects. An aspect is
         * defined by the method to intercept defined as a Type string and the method to call.
         *
         * The baseline file is a list of whitelisted callstacks that should be ignored when there
         * is a matching aspect. The callstacks are represented by method names separated by a
         * pipe (|) character. For instance "com.pkg.MyClass.method1|com.pkg.OtherClass.method2".
         */
        String[] splitArgs = agentArgs.split(";");
        String rulesFile = splitArgs[0];
        String baselineFile = splitArgs.length == 2 ? splitArgs[1] : null;

        Map<String, String> aspectsMap =
                RulesFile.parserRulesFile(rulesFile, Function.identity(), Agent::shortcutProcessor);
        LOGGER.info(String.format("Starting Aspect agent (%d rules)", aspectsMap.size()));

        Aspects aspects = new Aspects(aspectsMap);
        aspectsMap.forEach(
                (key, value) -> LOGGER.fine(String.format("Rule added %s=%s", key, value)));
        inst.addTransformer(new Transform(aspects), inst.isRetransformClassesSupported());

        if (!Baseline.getInstance().isGeneratingBaseline()) {
            Baseline.getInstance()
                    .parse(
                            baselineFile == null
                                    ? null
                                    : Files.newInputStream(Paths.get(baselineFile)));
        }

        if (inst.isRetransformClassesSupported()) {
            LOGGER.fine("Re-transformation enabled");

            /*
            Final classes are not transformed automatically by the transformer. In here, we manually go through
            all the loaded final classes and force a re-transform.
            */
            for (Class<?> loadedClass : inst.getAllLoadedClasses()) {
                String canonicalName = loadedClass.getCanonicalName();
                if (canonicalName == null) {
                    continue;
                }
                if (!aspects.hasClass(loadedClass.getCanonicalName().replace('.', '/'))) {
                    continue;
                }
                if ((loadedClass.getModifiers() & Modifier.FINAL) != 0) {
                    try {
                        LOGGER.fine("Transforming final class " + loadedClass.getName());
                        inst.retransformClasses(loadedClass);
                    } catch (UnmodifiableClassException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
