package com.android.tools.maven;

import static org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictContext;
import static org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictItem;
import static org.eclipse.aether.util.graph.transformer.ConflictResolver.VersionSelector;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import java.util.Map;
import java.util.Set;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;

/**
 * {@link VersionSelector} that implements the same strategy as Gradle, i.e. the highest version
 * wins.
 *
 * <p>It also makes sure that explicitly requested dependencies end up using the versions requested.
 */
public class HighestVersionSelector extends VersionSelector {

    private static final Ordering<ConflictResolver.ConflictItem> VERSION_ORDERING =
            Ordering.natural().onResultOf(HighestVersionSelector::getVersion);

    private final Map<String, Artifact> explicitlyRequested;

    public HighestVersionSelector(Set<Artifact> explicitlyRequested) {
        this.explicitlyRequested =
                Maps.uniqueIndex(explicitlyRequested, HighestVersionSelector::getArtifactKey);
    }

    @Override
    public void selectVersion(ConflictContext context) throws RepositoryException {
        Preconditions.checkArgument(!context.getItems().isEmpty(), "Empty conflict context.");

        //noinspection OptionalGetWithoutIsPresent - if the context was not empty, there's a max element.
        ConflictItem winner = context.getItems().stream().max(VERSION_ORDERING).get();

        context.setWinner(winner);

        // Check if we resolved a conflict for an artifact that was explicitly requested.
        Artifact winnerArtifact = context.getWinner().getDependency().getArtifact();
        Artifact requestedArtifact = explicitlyRequested.get(getArtifactKey(winnerArtifact));
        if (requestedArtifact != null
                && !requestedArtifact.getVersion().equals(winnerArtifact.getVersion())) {
            throw new RuntimeException(
                    "Explicitly requested " + requestedArtifact + " overridden by " + winner);
        }
    }

    private static Version getVersion(ConflictItem conflictItem) {
        try {
            String versionString = conflictItem.getDependency().getArtifact().getVersion();
            return new GenericVersionScheme().parseVersion(versionString);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getArtifactKey(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }
}
