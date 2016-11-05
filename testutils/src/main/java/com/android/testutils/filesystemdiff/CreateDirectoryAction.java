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
package com.android.testutils.filesystemdiff;

import com.android.utils.ILogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CreateDirectoryAction extends Action {
    private DirectoryEntry mSource;
    private DirectoryEntry mDestination;

    public CreateDirectoryAction(DirectoryEntry source, DirectoryEntry destination) {
        mSource = source;
        mDestination = destination;
    }

    @Override
    public DirectoryEntry getSourceEntry() {
        return mSource;
    }

    @Override
    public void execute(ILogger logger) {
        logger.verbose("Creating directory %s", mDestination.getPath());
        try {
            Files.createDirectory(mDestination.getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (FileSystemEntry child : mSource.getChildEntries()) {
            Path destinationPath = mDestination.getPath().resolve(child.getPath().getFileName());
            switch (child.getKind()) {
                case Directory: {
                    DirectoryEntry dest = new DirectoryEntry(destinationPath);
                    new CreateDirectoryAction((DirectoryEntry) child, dest).execute(logger);
                    break;
                }
                case SymbolicLink: {
                    SymbolicLinkEntry dest = new SymbolicLinkEntry(destinationPath,
                            ((SymbolicLinkEntry) child).getTarget());
                    new CreateSymbolicLinkAction((SymbolicLinkEntry) child, dest).execute(logger);
                    break;
                }
                case File: {
                    FileEntry dest = new FileEntry(destinationPath);
                    new CreateFileAction((FileEntry) child, dest).execute(logger);
                    break;
                }
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }
}
