package com.android.tools.maven;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;
import java.util.List;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;

/** Constructs Aether objects. */
class AetherUtils {

    static RepositorySystem getRepositorySystem() {
        DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();

        // Make sure we use the 'simple' implementation.
        List<LocalRepositoryManagerFactory> factories =
                serviceLocator.getServices(LocalRepositoryManagerFactory.class);
        for (LocalRepositoryManagerFactory factory : factories) {
            if (factory instanceof SimpleLocalRepositoryManagerFactory) {
                ((SimpleLocalRepositoryManagerFactory) factory).setPriority(100);
            }
        }

        return checkNotNull(serviceLocator.getService(RepositorySystem.class));
    }

    static DefaultRepositorySystemSession getRepositorySystemSession(
            RepositorySystem repositorySystem, Path localRepo) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setIgnoreArtifactDescriptorRepositories(true);
        session.setOffline(true);
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(
                        session, new LocalRepository(localRepo.toFile())));

        DependencyGraphTransformer transformer =
                new ConflictResolver(
                        new HighestVersionSelector(),
                        new JavaScopeSelector(),
                        new SimpleOptionalitySelector(),
                        new JavaScopeDeriver());
        session.setDependencyGraphTransformer(transformer);

        session.setRepositoryListener(new ResolutionErrorRepositoryListener());

        return session;
    }
}
