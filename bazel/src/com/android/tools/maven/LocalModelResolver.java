package com.android.tools.maven;

import java.nio.file.Path;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;

/**
 * {@link ModelResolver} implementation for a "normal", local repository.
 *
 * <p>Delegates to Aether's {@link LocalRepositoryManager} to calculate paths.
 */
class LocalModelResolver implements ModelResolver {
    private final Path mRepoDir;
    private final LocalRepositoryManager mLocalRepositoryManager;

    LocalModelResolver(Path repoDir) {
        mRepoDir = repoDir;

        RepositorySystem repositorySystem = AetherUtils.getRepositorySystem();
        DefaultRepositorySystemSession session =
                AetherUtils.getRepositorySystemSession(repositorySystem, mRepoDir);
        mLocalRepositoryManager =
                repositorySystem.newLocalRepositoryManager(
                        session, new LocalRepository(mRepoDir.toFile()));
    }

    @Override
    @SuppressWarnings("deprecation") // This is the ModelResolver API
    public ModelSource resolveModel(String groupId, String artifactId, String version)
            throws UnresolvableModelException {
        String path = getPathForArtifact(groupId, artifactId, version, "pom");
        return new FileModelSource(mRepoDir.resolve(path).toFile());
    }

    public String getPathForArtifact(
            String groupId, String artifactId, String version, String extension) {
        return mLocalRepositoryManager.getPathForLocalArtifact(
                new DefaultArtifact(groupId, artifactId, extension, version));
    }

    @Override
    @SuppressWarnings("deprecation") // This is the ModelResolver API
    public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    @Override
    public void addRepository(Repository repository) throws InvalidRepositoryException {}

    @Override
    public void addRepository(Repository repository, boolean replace)
            throws InvalidRepositoryException {}

    @Override
    public ModelResolver newCopy() {
        return new LocalModelResolver(mRepoDir);
    }
}
