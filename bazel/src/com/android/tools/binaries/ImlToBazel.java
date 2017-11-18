/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.binaries;

import com.android.tools.bazel.ImlToIr;
import com.android.tools.bazel.IrToBazel;
import com.android.tools.bazel.StudioConfiguration;
import com.android.tools.bazel.ir.IrProject;
import com.android.tools.utils.WorkspaceUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;

public class ImlToBazel {
    public static void main(String[] strings) throws Exception {

        Path workspace = null;
        boolean dryRun = false;
        String project = "tools/idea";
        String imlGraph = null;

        Iterator<String> args = Arrays.asList(strings).iterator();
        while (args.hasNext()) {
            String arg = args.next();
            if (arg.equals("--workspace") && args.hasNext()) {
                workspace = Paths.get(args.next());
            } else if (arg.equals("--project_path") && args.hasNext()) {
                project = args.next();
            } else if (arg.equals("--iml_graph") && args.hasNext()) {
                imlGraph = args.next();
            } else if (arg.equals("--dry_run")){
                dryRun = true;
            } else {
                System.err.println("Unknown argument: " + arg);
                System.exit(1);
            }
        }
        if (workspace == null) {
            workspace = WorkspaceUtils.findWorkspace();
        }

        long now = System.nanoTime();
        try {
            run(workspace, dryRun, project, imlGraph);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
        long milli = (System.nanoTime() - now) / 1000000L;
        System.err.println(String.format("Total time: %d.%03ds", milli / 1000, milli % 1000));

        // JPS creates a shared thread executor that creates non-daemon threads. There is no way to access that to shut it down, so we exit
        System.exit(0);
    }

    public static int run(Path workspace, boolean dryRun, String project, String imlGraph)
            throws IOException {
        PrintWriter writer = new PrintWriter(System.err, true);
        StudioConfiguration configuration = new StudioConfiguration();

        ImlToIr imlToIr = new ImlToIr();
        IrToBazel irToBazel = new IrToBazel(dryRun);

        IrProject irProject = imlToIr.convert(workspace, project, imlGraph, writer);
        return irToBazel.convert(irProject, writer, configuration);
    }
}
