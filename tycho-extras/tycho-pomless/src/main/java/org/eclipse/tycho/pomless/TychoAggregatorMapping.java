/*******************************************************************************
 * Copyright (c) 2019, 2020 Lablicate GmbH and others.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * Christoph Läubrich (Lablicate GmbH) - initial API and implementation
 * Christoph Läubrich - add type prefix to name
 *******************************************************************************/
package org.eclipse.tycho.pomless;

import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Model;

@Named("tycho-aggregator")
@Singleton
public class TychoAggregatorMapping extends AbstractTychoMapping {

    private static final String TYCHO_AUTOMATIC_GENERATED_FILE_HEADER_PREFIX = "## tycho automatic module detection";

    private static final String TYCHO_AUTOMATIC_GENERATED_FILE_HEADER = TYCHO_AUTOMATIC_GENERATED_FILE_HEADER_PREFIX
            + " " + UUID.randomUUID().toString();

    public static final String TYCHO_POM = "pom.tycho";

    private static final Set<String> COMMON_NAMES = Set.of(System.getProperty(TYCHO_POMLESS_AGGREGATOR_NAMES_PROPERTY,
            "bundles,plugins,tests,features,sites,products,releng").split(","));

    @Override
    protected String getPackaging() {
        return "pom";
    }

    @Override
    public float getPriority() {
        return -10;
    }

    @Override
    protected boolean isValidLocation(Path location) {
        return getFileName(location).equals(TYCHO_POM);
    }

    @Override
    protected File getPrimaryArtifact(File dir) {
        File file = new File(dir, TYCHO_POM);
        if (file.exists() && isCurrent(file)) {
            return file;
        }
        if (COMMON_NAMES.contains(dir.getName().toLowerCase())) {
            logger.debug("Scanning folder " + dir + " for modules");
            Set<String> modules = new TreeSet<>();
            try (var subFolders = Files.newDirectoryStream(dir.toPath(), Files::isDirectory)) {
                for (Path subfolder : subFolders) {
                    PomReference reference = locatePomReference(subfolder, null);
                    if (reference != null) {
                        String name = getFileName(subfolder);
                        modules.add(name);
                        logger.debug("Found pom " + reference.getPomFile().getName() + " in subfolder " + name);
                    }
                }
            } catch (IOException e) { // assume empty
            }
            if (!modules.isEmpty()) {
                file.deleteOnExit();
                Stream<CharSequence> lines = concat(of(TYCHO_AUTOMATIC_GENERATED_FILE_HEADER), modules.stream());
                try {
                    Files.write(file.toPath(), lines::iterator, getPrimaryArtifactCharset());
                } catch (IOException e) {
                    throw new RuntimeException("writing modules file failed", e);
                }
                return file;
            }

        } else {
            logger.debug("Skip folder " + dir + " because it does not match any common name " + COMMON_NAMES);
        }
        return null;
    }

    @Override
    protected void initModel(Model model, Reader artifactReader, Path artifactFile) throws IOException {
        logger.debug("Generate aggregator pom for " + artifactFile);
        for (String module : readModules(artifactReader)) {
            logger.debug("Adding module " + module);
            model.getModules().add(module);
        }
        model.setArtifactId(getFileName(artifactFile.getParent()));
        model.setName("[aggregator] " + model.getArtifactId());
    }

    /**
     * Reads the modules of a {@value #TYCHO_POM} file, ignoring blank lines and comments.
     */
    public static List<String> readModules(Reader moduleList) throws IOException {
        try (BufferedReader reader = new BufferedReader(moduleList)) {
            return reader.lines().filter(l -> !l.startsWith("#") && !l.isBlank()).map(String::strip).toList();
        }
    }

    /**
     * Returns whether the given {@value #TYCHO_POM} file was written by Tycho itself instead of by
     * the user.
     */
    public static boolean isGenerated(Path moduleList) {
        try (var lines = Files.lines(moduleList, StandardCharsets.UTF_8)) {
            return lines.findFirst().filter(l -> l.startsWith(TYCHO_AUTOMATIC_GENERATED_FILE_HEADER_PREFIX))
                    .isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isCurrent(File file) {
        try (var lines = Files.lines(file.toPath(), getPrimaryArtifactCharset())) {
            String firstLine = lines.findFirst().orElse(null);
            return firstLine == null || !firstLine.startsWith(TYCHO_AUTOMATIC_GENERATED_FILE_HEADER_PREFIX)
                    || firstLine.equals(TYCHO_AUTOMATIC_GENERATED_FILE_HEADER);
        } catch (IOException e) {
            //can't be sure, assume it is not stale then...
        }
        return true;
    }

}
