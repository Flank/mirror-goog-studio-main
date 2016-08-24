/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.form;

import com.android.tools.utils.JarOutputCompiler;
import com.android.tools.utils.Unzipper;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;

import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class FormCompiler extends JarOutputCompiler implements NestedFormLoader {

    private File mOutDir;
    private InstrumentationClassFinder mFinder;
    private final HashMap<String, LwRootContainer> mCache = new HashMap<>();
    private final List<File> mForms = new ArrayList<>();

    public FormCompiler() {
        super("formc");
    }

    public static void main(String[] args) throws IOException {
        System.exit(new FormCompiler().run(Arrays.asList(args)));
    }

    @Override
    protected void compile(List<String> files, String classPath, File outDir) throws IOException {
        // Files can contain .jar and .form which will be added together into one output jar.
        mOutDir = outDir;
        final ArrayList<URL> urls = new ArrayList<>();

        addUrlsTo(System.getProperty("java.class.path"), urls);
        addUrlsTo(System.getProperty("sun.boot.class.path"), urls);
        addUrlsTo(classPath, urls);

        for (String file : files) {
            if (file.endsWith(".jar")) {
                addUrlsTo(file, urls);
                Unzipper unzipper = new Unzipper();
                unzipper.unzip(new File(file), outDir);
            } else if (file.endsWith(".form")) {
                mForms.add(new File(file));
            }
        }

        mFinder = new InstrumentationClassFinder(urls.toArray(new URL[urls.size()]));
        try {
            instrumentForms(mFinder, mForms);
        } finally {
            mFinder.releaseResources();
        }
    }

    private void addUrlsTo(String classPath, ArrayList<URL> urls) throws MalformedURLException {
        for (StringTokenizer token = new StringTokenizer(classPath, ":"); token.hasMoreTokens(); ) {
            urls.add(new File(token.nextToken()).toURI().toURL());
        }
    }

    private void instrumentForms(final InstrumentationClassFinder finder, List<File> forms) {
        if (forms.isEmpty()) {
            throw new IllegalArgumentException("No forms to instrument found");
        }

        final HashMap<String, File> class2form = new HashMap<>();
        for (File form : forms) {
            final LwRootContainer rootContainer;
            try {
                rootContainer = Utils.getRootContainer(form.toURI().toURL(),
                        new CompiledClassPropertiesProvider(finder.getLoader()));
            } catch (AlienFormFileException e) {
                // Ignore non-IDEA forms.
                continue;
            } catch (Exception e) {
                throw new RuntimeException("Cannot process form file " + form.getAbsolutePath(), e);
            }

            final String classToBind = rootContainer.getClassToBind();
            if (classToBind == null) {
                continue;
            }

            File classFile = getClassFile(classToBind);
            if (classFile == null) {
                throw new RuntimeException(String.format("%s: Class to bind does not exist: %s",
                        form.getAbsolutePath(), classToBind));
            }

            final File alreadyProcessedForm = class2form.get(classToBind);
            if (alreadyProcessedForm != null) {
                System.err.println(String.format(
                        "%s: The form is bound to the class %s.\n" +
                        "Another form %s is also bound to this class.",
                        form.getAbsolutePath(),
                        classToBind,
                        alreadyProcessedForm.getAbsolutePath()));
                continue;
            }
            class2form.put(classToBind, form);

            InstrumenterClassWriter classWriter =
                    new InstrumenterClassWriter(ClassWriter.COMPUTE_FRAMES, finder);
            final AsmCodeGenerator codeGenerator =
                    new AsmCodeGenerator(rootContainer, finder, this, false, classWriter);
            codeGenerator.patchFile(classFile);
            final FormErrorInfo[] warnings = codeGenerator.getWarnings();

            for (FormErrorInfo warning : warnings) {
                System.err.println(form.getAbsolutePath() + ": " + warning.getErrorMessage());
            }
            final FormErrorInfo[] errors = codeGenerator.getErrors();
            for (FormErrorInfo error : errors) {
                System.err.println(form.getAbsolutePath() + ": " + error.getErrorMessage());
            }
            if (errors.length > 0) {
                throw new RuntimeException("Errors found during form compilation");
            }
        }
    }

    /**
     * Takes {@code className} of the form "a.b.c.d", and returns one of the form "a/b/c$d" for
     * the first .class file found, or {@code null} otherwise.
     */
    private String getClassName(String className) {
        String[] split = className.split("\\.");
        for (int i = split.length - 1; i >= 0; i--) {
            String candidate = split[0];
            for (int j = 1; j < split.length; j++) {
                candidate += (j > i ? "$" : "/") + split[j];
            }
            File file = new File(mOutDir, candidate + ".class");
            if (file.exists()) {
                return candidate;
            }
        }
        return null;
    }

    private File getClassFile(String name) {
        name = getClassName(name);
        return name == null ? null : new File(mOutDir, name + ".class");
    }

    @Override
    public LwRootContainer loadForm(String formFilePath) throws Exception {
        if (mCache.containsKey(formFilePath)) {
            return mCache.get(formFilePath);
        }

        InputStream stream = null;
        String lowerFormFilePath = formFilePath.toLowerCase();
        // Try to find the nested form:
        for (File file : mForms) {
            String name = file.getAbsolutePath().replace(File.separatorChar, '/').toLowerCase();
            if (name.endsWith(lowerFormFilePath)) {
                stream = new FileInputStream(file);
                break;
            }
        }
        if (stream == null) {
            stream = mFinder.getLoader().getResourceAsStream(formFilePath);
        }

        if (stream != null) {
            // Stream is closed by SAXParser.
            final LwRootContainer container = Utils.getRootContainer(stream, null);
            mCache.put(formFilePath, container);
            return container;
        } else {
            throw new Exception("Cannot find nested form: " + formFilePath);
        }
    }

    @Override
    public String getClassToBindName(LwRootContainer container) {
        final String className = container.getClassToBind();
        String result = getClassName(className);
        return result != null ? result.replace('/', '.') : className;
    }
}
