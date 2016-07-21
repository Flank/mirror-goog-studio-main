package com.android.tools;

import junit.framework.TestCase;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public class BazelTestSuiteRunner extends Suite {

    public BazelTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError, ClassNotFoundException, IOException {
        super(builder, suiteClass, getTestClasses());
    }

    private static Class<?>[] getTestClasses() throws ClassNotFoundException, IOException {
        List<Class<?>> testClasses = new ArrayList<>();
        String name = System.getProperty("test.suite.jar");

        final ClassLoader loader = BazelTestSuite.class.getClassLoader();
        if (loader instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader)loader).getURLs()) {
                if (url.getPath().endsWith(name)) {
                    testClasses.addAll(getTestClasses(url, loader));
                }
            }
        }
        return testClasses.toArray(new Class<?>[testClasses.size()]);
    }

    private static List<Class<?>> getTestClasses(URL url, ClassLoader loader) throws ClassNotFoundException, IOException {
        List<Class<?>> testClasses = new ArrayList<>();
        File file = new File(url.getFile());
        if (file.exists()) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (!ze.isDirectory()) {
                        String name = className(ze.getName());
                        if (name != null) {
                            Class<?> aClass = loader.loadClass(name);
                            if (aClass.isAnnotationPresent(RunWith.class) || TestCase.class.isAssignableFrom(aClass)) {
                                testClasses.add((Class<? extends TestCase>) aClass);
                            }
                        }
                    }
                }
            } catch (ZipException e) {
                System.err.println("Error while opening jar " + file.getName() + " : " + e.getMessage());
            }
        }
        return testClasses;
    }

    private static String className(String name) {
        if (name.endsWith(".class")) {
            return name.replaceAll("/", ".").replaceAll(".class$","");
        }
        return null;
    }
}
