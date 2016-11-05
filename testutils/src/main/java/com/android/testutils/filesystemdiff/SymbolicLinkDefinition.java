package com.android.testutils.filesystemdiff;

import java.nio.file.Path;

public class SymbolicLinkDefinition {
    private Path mPath;
    private Path mTarget;

    public SymbolicLinkDefinition(Path path, Path target) {
        this.mPath = path;
        this.mTarget = target;
    }

    public Path getPath() {
        return mPath;
    }

    public Path getTarget() {
        return mTarget;
    }
}
