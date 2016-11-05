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

public class DeleteSymbolicLinkAction extends Action {
    private SymbolicLinkEntry mEntry;

    public DeleteSymbolicLinkAction(SymbolicLinkEntry entry) {
        mEntry = entry;
    }

    @Override
    public SymbolicLinkEntry getSourceEntry() {
        return mEntry;
    }

    @Override
    public void execute(ILogger logger) {
        try {
            logger.verbose("Deleting symbolic link %s", mEntry.getPath());
            Files.delete(mEntry.getPath());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
