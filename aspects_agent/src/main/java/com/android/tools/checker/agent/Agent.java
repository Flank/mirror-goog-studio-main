package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import com.android.tools.checker.Assertions;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

@SuppressWarnings("unused") // Used via -javaagent path
public class Agent {
    private static final Logger LOGGER = Logger.getLogger(Transform.class.getName());

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
         * agentArgs should be the path to the rules file. The rules file is simply a list
         * of key=value pairs defining the aspects.
         * An aspect is defined by the method to intercept defined as a Type string and the
         * method to call.
         */

        Map<String, String> aspectsMap =
                RulesFile.parserRulesFile(agentArgs, Function.identity(), Agent::shortcutProcessor);
        LOGGER.info(String.format("Starting Aspect agent (%d rules)", aspectsMap.size()));

        Aspects aspects = new Aspects(aspectsMap);

        aspectsMap
                .entrySet()
                .forEach(
                        entry ->
                                LOGGER.info(
                                        String.format(
                                                "Rule added %s=%s",
                                                entry.getKey(), entry.getValue())));
        inst.addTransformer(new Transform(aspects), inst.isRetransformClassesSupported());

        if (inst.isRetransformClassesSupported()) {
            LOGGER.info("Re-transformation enabled");

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
                        LOGGER.info("Transforming final class " + loadedClass.getName());
                        inst.retransformClasses(loadedClass);
                    } catch (UnmodifiableClassException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
