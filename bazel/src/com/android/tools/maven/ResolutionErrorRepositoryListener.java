package com.android.tools.maven;

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
            for (Exception exception : event.getExceptions()) {
                System.err.println(exception.getMessage());
            }
            System.err.println("Aether failed to resolve some artifacts, aborting.");
            System.exit(1);
        }
    }
}
