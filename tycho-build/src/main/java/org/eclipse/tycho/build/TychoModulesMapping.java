/*******************************************************************************
 * Copyright (c) 2026 Lars Vogel and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Lars Vogel - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.build;

import static org.eclipse.tycho.pomless.TychoAggregatorMapping.TYCHO_POM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.ModelWriter;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.tycho.pomless.TychoAggregatorMapping;
import org.sonatype.maven.polyglot.PolyglotModelUtil;
import org.sonatype.maven.polyglot.mapping.Mapping;

/**
 * Adds the modules listed in a {@value TychoAggregatorMapping#TYCHO_POM} file to the model of the
 * {@code pom.xml} next to it, so that a project can keep a regular parent POM but maintain its
 * module list outside of it.
 */
@Named("tycho-modules")
@Singleton
public class TychoModulesMapping implements Mapping, ModelReader {

    private static final String POM_XML = "pom.xml";

    @Inject
    protected PlexusContainer container;

    @Inject
    protected Logger logger;

    @Override
    public File locatePom(File dir) {
        // the pom.xml is found by the default mechanism, this mapping only enhances it
        return null;
    }

    @Override
    public boolean accept(Map<String, ?> options) {
        Path location = location(options);
        return location != null && hasModuleList(location);
    }

    private static boolean hasModuleList(Path location) {
        if (!POM_XML.equals(String.valueOf(location.getFileName()))) {
            return false;
        }
        Path moduleList = moduleList(location);
        // a generated file always belongs to a directory without a pom.xml, if one is left over
        // from a previous build it must not silently contribute modules here
        return moduleList != null && Files.isRegularFile(moduleList) && !TychoAggregatorMapping.isGenerated(moduleList);
    }

    private static Path moduleList(Path pomXml) {
        Path directory = pomXml.getParent();
        return directory == null ? null : directory.resolve(TYCHO_POM);
    }

    @Override
    public float getPriority() {
        // must be higher than the priority of the mapping that usually reads a pom.xml
        return 50;
    }

    @Override
    public String getFlavour() {
        return "default";
    }

    @Override
    public ModelReader getReader() {
        return this;
    }

    @Override
    public ModelWriter getWriter() {
        return lookup(ModelWriter.class);
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        return addModules(lookup(ModelReader.class).read(input, options), input.toPath());
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        return addModules(lookup(ModelReader.class).read(input, options), location(options));
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        return addModules(lookup(ModelReader.class).read(input, options), location(options));
    }

    private Model addModules(Model model, Path pomXml) throws IOException {
        Path moduleList = pomXml == null ? null : moduleList(pomXml);
        if (moduleList == null || !Files.isRegularFile(moduleList)) {
            return model;
        }
        try (Reader reader = Files.newBufferedReader(moduleList, StandardCharsets.UTF_8)) {
            for (String module : TychoAggregatorMapping.readModules(reader)) {
                if (!model.getModules().contains(module)) {
                    logger.debug("Adding module " + module + " from " + moduleList);
                    model.getModules().add(module);
                }
            }
        }
        return model;
    }

    private static Path location(Map<String, ?> options) {
        String location = PolyglotModelUtil.getLocation(options);
        if (location != null) {
            try {
                return Path.of(location);
            } catch (InvalidPathException e) {
            }
        }
        return null;
    }

    private <T> T lookup(Class<T> role) {
        try {
            return container.lookup(role, getFlavour());
        } catch (ComponentLookupException e) {
            throw new RuntimeException("can't lookup " + role.getName(), e);
        }
    }

}
