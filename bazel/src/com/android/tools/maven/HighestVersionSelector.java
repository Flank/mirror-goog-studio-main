package com.android.tools.maven;

import static org.eclipse.aether.util.graph.transformer.ConflictResolver.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import java.util.Optional;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;

/**
 * {@link VersionSelector} that implements the same strategy as Gradle, i.e. the highest version
 * wins.
 */
class HighestVersionSelector extends VersionSelector {

    private static final Ordering<ConflictResolver.ConflictItem> VERSION_ORDERING =
            Ordering.natural().onResultOf(HighestVersionSelector::getVersion);

    @Override
    public void selectVersion(ConflictContext context) throws RepositoryException {
        Preconditions.checkArgument(!context.getItems().isEmpty(), "Empty conflict context.");

        Optional<ConflictResolver.ConflictItem> winner =
                context.getItems().stream().max(VERSION_ORDERING);

        //noinspection OptionalGetWithoutIsPresent - if the context was not empty, there's a max element.
        context.setWinner(winner.get());
    }

    private static Version getVersion(ConflictItem conflictItem) {
        try {
            return new GenericVersionScheme()
                    .parseVersion(conflictItem.getDependency().getArtifact().getVersion());
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }
}
