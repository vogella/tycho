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

import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelReader;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.Test;
import org.sonatype.maven.polyglot.PolyglotModelManager;

@PlexusTest
public class TychoModulesMappingTest {

    @Inject
    private PolyglotModelManager polyglotModelManager;

    @Test
    public void testModuleListFile() throws Exception {
        assertEquals(List.of("declared.in.pom", "listed.in.module.file"), readModules("modulelist"),
                "modules of pom.tycho must be added to the modules of the pom.xml");
    }

    @Test
    public void testGeneratedModuleListFileIsIgnored() throws Exception {
        assertEquals(List.of("declared.in.pom"), readModules("modulelist-generated"),
                "a left over generated pom.tycho must not contribute modules to a pom.xml");
    }

    private List<String> readModules(String project) throws Exception {
        File pom = new File(getBasedir(), "src/test/resources/mapping/" + project + "/pom.xml");
        Map<String, ?> options = Map.of(ModelProcessor.SOURCE, new FileModelSource(pom));
        ModelReader reader = polyglotModelManager.getReaderFor(options);
        Model model = reader.read(pom, options);
        return model.getModules();
    }

}
