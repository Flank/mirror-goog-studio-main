package com.android.tools.maven;

import com.google.common.base.Throwables;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

/**
 * {@link org.eclipse.aether.RepositoryListener} that fails the generator binary if we can't resolve
 * some artifact in the local repository.
 *
 * <p>Just throwing an exception doesn't work, since aether's listener machinery catches and logs
 * them. Since we don't have proper SLF4J setup, this means they are ignored.
 */
class ResolutionErrorRepositoryListener extends AbstractRepositoryListener {
    @Override
    public void artifactResolved(RepositoryEvent event) {
        if (!event.getExceptions().isEmpty()) {
            System.err.println(Throwables.getStackTraceAsString(event.getException()));
            System.exit(1);
        }
    }
}
