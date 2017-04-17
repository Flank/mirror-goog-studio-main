/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FD_JARS;

import com.google.common.io.Files;
import java.io.File;
import org.gradle.api.Project;
import org.gradle.api.file.RelativePath;

/**
 * Cache to library prepareTask.
 *
 * Each project creates its own version of LibraryDependency, but they all represent the
 * same library. This creates a single task that will unarchive the aar so that this is done only
 * once even for multi-module projects where 2+ modules depend on the same library.
 *
 * The prepareTask is created in the root project always.
 */
public class LibraryCache {

    public static void unzipAar(final File bundle, final File folderOut, final Project project) {
        for (File f : Files.fileTreeTraverser().postOrderTraversal(folderOut)) {
            f.delete();
        }

        folderOut.mkdirs();

        project.copy(
                copySpec -> {
                    copySpec.from(project.zipTree(bundle));
                    copySpec.into(folderOut);
                    copySpec.filesMatching(
                            "**/*.jar",
                            details -> {
                                /*
                                 * For each jar, check where it is. /classes.jar, /lint.jar and jars in
                                 * /libs are moved inside the FD_JARS directory. Jars inside /assets or
                                 * /res/raw are kept where they were. All other jars are ignored and a
                                 * warning is issued.
                                 */
                                String path = details.getRelativePath().getPathString();
                                if (path.equals("classes.jar")
                                        || path.equals("lint.jar")
                                        || path.startsWith("libs/")) {
                                    details.setRelativePath(
                                            new RelativePath(false, FD_JARS)
                                                    .plus(details.getRelativePath()));
                                } else if (!path.startsWith("res/raw/*")
                                        && !path.startsWith("assets/*")) {
                                    project.getLogger()
                                            .warn(
                                                    "Jar found at unexpected path ("
                                                            + path
                                                            + ") in "
                                                            + bundle
                                                            + " and will be ignored. Jars should be "
                                                            + "placed inside 'jars' folder to be merged into dex. Jars "
                                                            + "that are in assets/ or res/raw/ will be copied as-is.");
                                }
                            });
                });
    }
}
