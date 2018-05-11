package com.android.tools.maven;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;

/** Constructs Aether objects. */
public class AetherUtils {
    static final RemoteRepository MAVEN_CENTRAL =
            new RemoteRepository.Builder(
                            "Maven Central", "default", "https://repo1.maven.org/maven2/")
                    .build();

    static final RemoteRepository JCENTER =
            new RemoteRepository.Builder("JCenter", "default", "https://jcenter.bintray.com/")
                    .build();

    static final RemoteRepository GOOGLE =
            new RemoteRepository.Builder("Google", "default", "https://maven.google.com/").build();

    static final RemoteRepository GRADLE =
            new RemoteRepository.Builder(
                            "Gradle",
                            "default",
                            "https://repo.gradle.org/gradle/libs-releases-local/")
                    .build();

    static final RemoteRepository INTELLIJ =
            new RemoteRepository.Builder(
                            "IntelliJ deps",
                            "default",
                            "https://jetbrains.bintray.com/intellij-third-party-dependencies")
                    .build();

    public static final ImmutableList<RemoteRepository> REPOSITORIES =
            ImmutableList.of(MAVEN_CENTRAL, JCENTER, GOOGLE, GRADLE, INTELLIJ);

    private AetherUtils() {}

    static RepositorySystem getRepositorySystem() {
        DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();

        serviceLocator.addService(
                RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        serviceLocator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        // Make sure we use the 'simple' implementation of LocalRepositoryManagerFactory. Otherwise
        // we generate unnecessary files (related to caching) in the local repo.
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
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(
                        session, new LocalRepository(localRepo.toFile())));

        session.setRepositoryListener(new ResolutionErrorRepositoryListener());

        session.setConfigProperty(
                ArtifactDescriptorReaderDelegate.class.getName(),
                new HandleAarDescriptorReaderDelegate());

        return session;
    }

    public static AndDependencySelector buildDependencySelector(List<Exclusion> exclusions) {
        return new AndDependencySelector(
                new OptionalDependencySelector(),
                new ScopeDependencySelector(
                        ImmutableList.of(JavaScopes.COMPILE, JavaScopes.RUNTIME), null),
                new ExclusionDependencySelector(
                        ImmutableList.of(
                                new Exclusion("maven-plugins", "*", "*", "*"),
                                new Exclusion("*", "*", "*", "plugin"))),
                new ExclusionDependencySelector(exclusions));
    }
}
