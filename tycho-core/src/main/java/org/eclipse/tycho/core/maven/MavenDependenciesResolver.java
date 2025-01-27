/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.core.maven;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.RepositorySessionDecorator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

@Component(role = MavenDependenciesResolver.class)
public class MavenDependenciesResolver {

    @Requirement
    RepositorySystem repoSystem;

    @Requirement
    List<RepositorySessionDecorator> decorators;

    @Requirement
    Logger logger;

    /**
     * Resolves the specified dependencies including their transitive ones.
     *
     * @param project
     *            The project whose dependencies should be resolved, must not be {@code null}.
     * @param dependencies
     * @param scopesToResolve
     *            The dependency scopes that should be resolved, may be {@code null}.
     * @param session
     *            The current build session, must not be {@code null}.
     * @return The transitive dependencies of the specified project that match the requested scopes,
     *         never {@code null}.
     * @throws DependencyCollectionException
     * @throws DependencyResolutionException
     */
    public Collection<org.apache.maven.artifact.Artifact> resolve(MavenProject project,
            Collection<org.apache.maven.model.Dependency> dependencies, Collection<String> scopesToResolve,
            MavenSession session) throws DependencyCollectionException, DependencyResolutionException {
        if (dependencies.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Artifact> resultSet = new HashSet<>();

        CollectRequest collect = new CollectRequest();
        RepositorySystemSession repositorySession = getRepositorySession(project, session);
        ArtifactTypeRegistry stereotypes = repositorySession.getArtifactTypeRegistry();
        for (org.apache.maven.model.Dependency dependency : dependencies) {
            collect.addDependency(RepositoryUtils.toDependency(dependency, stereotypes));
        }
        DependencyManagement dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null) {
            for (org.apache.maven.model.Dependency dependency : dependencyManagement.getDependencies()) {
                collect.addManagedDependency(RepositoryUtils.toDependency(dependency, stereotypes));
            }
        }
        collect.setRepositories(project.getRemoteProjectRepositories());

        CollectResult collectResult = repoSystem.collectDependencies(repositorySession, collect);
        DependencyNode rootNode = collectResult.getRoot();

        CumulativeScopeArtifactFilter scopeArtifactFilter = new CumulativeScopeArtifactFilter(scopesToResolve);
        DependencyRequest dependencyRequest = new DependencyRequest(collect, new DependencyFilter() {

            @Override
            public boolean accept(DependencyNode node, List<DependencyNode> parents) {

                Artifact artifact = RepositoryUtils.toArtifact(node.getArtifact());
                return artifact != null && scopeArtifactFilter.include(artifact);
            }
        });
        dependencyRequest.setRoot(rootNode);

        DependencyResult dependencyResult = repoSystem.resolveDependencies(repositorySession, dependencyRequest);
        List<ArtifactResult> artifactResults = dependencyResult.getArtifactResults();
        for (ArtifactResult ar : artifactResults) {
            DependencyNode node = ar.getRequest().getDependencyNode();
            org.eclipse.aether.graph.Dependency dependency = node.getDependency();
            if (ar.isResolved()) {
                Artifact artifact = RepositoryUtils.toArtifact(dependency.getArtifact());
                if (scopeArtifactFilter.include(artifact)) {
                    resultSet.add(artifact);

                }
            } else {
                logger.error("Cannot resolve " + dependency);
                for (Exception e : ar.getExceptions()) {
                    logger.error("", e);
                }
            }
        }
        return resultSet;
    }

    private RepositorySystemSession getRepositorySession(MavenProject project, MavenSession session) {
        RepositorySystemSession repositorySession = session.getRepositorySession();
        for (RepositorySessionDecorator decorator : decorators) {
            RepositorySystemSession decorated = decorator.decorate(project, repositorySession);
            if (decorated != null) {
                repositorySession = decorated;
            }
        }
        return repositorySession;
    }

    /**
     * Resolves the highest version of the given dependency
     * 
     * @param project
     * @param session
     * @param dependency
     * @return
     * @throws VersionRangeResolutionException
     * @throws ArtifactResolutionException
     */
    public Artifact resolveHighestVersion(MavenProject project, MavenSession session,
            org.apache.maven.model.Dependency dependency)
            throws VersionRangeResolutionException, ArtifactResolutionException {
        RepositorySystemSession repositorySession = getRepositorySession(project, session);
        ArtifactTypeRegistry stereotypes = repositorySession.getArtifactTypeRegistry();
        DefaultArtifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                stereotypes.get(dependency.getType()).getExtension(), dependency.getVersion());
        VersionRangeRequest request = new VersionRangeRequest(artifact, project.getRemoteProjectRepositories(), null);
        VersionRangeResult versionResult = repoSystem.resolveVersionRange(repositorySession, request);
        Version highestVersion = versionResult.getHighestVersion();
        if (highestVersion != null) {
            ArtifactRequest artifactRequest = new ArtifactRequest(artifact.setVersion(highestVersion.toString()),
                    project.getRemoteProjectRepositories(), null);
            ArtifactResult result = repoSystem.resolveArtifact(repositorySession, artifactRequest);
            return RepositoryUtils.toArtifact(result.getArtifact());
        }
        return null;
    }
}
