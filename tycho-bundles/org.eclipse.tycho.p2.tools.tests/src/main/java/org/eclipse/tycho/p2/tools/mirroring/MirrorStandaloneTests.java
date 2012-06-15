/*******************************************************************************
 * Copyright (c) 2011, 2012 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2.tools.mirroring;

import static org.eclipse.tycho.p2.tools.mirroring.MirrorApplicationServiceTest.repoFile;
import static org.eclipse.tycho.p2.tools.mirroring.MirrorApplicationServiceTest.sourceRepos;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;

import org.eclipse.tycho.BuildOutputDirectory;
import org.eclipse.tycho.core.facade.MavenContextImpl;
import org.eclipse.tycho.core.facade.MavenLogger;
import org.eclipse.tycho.p2.tools.DestinationRepositoryDescriptor;
import org.eclipse.tycho.p2.tools.FacadeException;
import org.eclipse.tycho.p2.tools.RepositoryReferences;
import org.eclipse.tycho.p2.tools.mirroring.facade.IUDescription;
import org.eclipse.tycho.p2.tools.mirroring.facade.MirrorOptions;
import org.eclipse.tycho.test.util.MemoryLog;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MirrorStandaloneTests {
    private static final String DEFAULT_NAME = "dummy";

    private DestinationRepositoryDescriptor destinationRepo;

    private MirrorApplicationServiceImpl subject;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BuildOutputDirectory targetFolder;

    @Before
    public void initTestContext() {
        MavenLogger logger = new MemoryLog(true);
        destinationRepo = new DestinationRepositoryDescriptor(tempFolder.newFolder("dest"), DEFAULT_NAME);
        subject = new MirrorApplicationServiceImpl();
        MavenContextImpl mavenContext = new MavenContextImpl();
        mavenContext.setLogger(logger);
        subject.setMavenContext(mavenContext);
        targetFolder = new BuildOutputDirectory(tempFolder.getRoot());
    }

    @Test
    public void testMirrorAllUnits() throws Exception {
        subject.mirrorStandalone(e342PlusFragmentsRepo(), destinationRepo, null, new MirrorOptions(), targetFolder);
        assertEquals(3, getMirroredBundleFiles().length);
        assertTrue(repoFile(destinationRepo, "plugins/org.eclipse.core.runtime_3.4.0.v20080512.jar").exists());
        assertTrue(repoFile(destinationRepo,
                "plugins/org.eclipse.equinox.launcher.gtk.linux.x86_64_1.0.101.R34x_v20080731.jar").exists());
        assertTrue(repoFile(destinationRepo,
                "plugins/org.eclipse.equinox.launcher.win32.win32.x86_1.0.101.R34x_v20080731.jar").exists());
    }

    @Test
    public void testMirrorSpecificUnitLatestVersion() throws Exception {
        mirrorCoreRuntimeBundle();
        assertEquals(1, getMirroredBundleFiles().length);
        assertTrue(repoFile(destinationRepo, "plugins/org.eclipse.core.runtime_3.4.0.v20080512.jar").exists());
    }

    private File[] getMirroredBundleFiles() {
        return new File(destinationRepo.getLocation(), "plugins").listFiles();
    }

    private void mirrorCoreRuntimeBundle() throws FacadeException {
        subject.mirrorStandalone(e342PlusFragmentsRepo(), destinationRepo,
                Collections.singletonList(new IUDescription("org.eclipse.core.runtime", null)), new MirrorOptions(),
                targetFolder);
    }

    @Test
    public void testMirrorSpecificUnitSpecificVersion() throws Exception {
        subject.mirrorStandalone(e342PlusFragmentsRepo(), destinationRepo,
                Collections.singletonList(new IUDescription("org.eclipse.core.runtime", "3.4.0.v20080512")),
                new MirrorOptions(), targetFolder);
        assertEquals(1, getMirroredBundleFiles().length);
        assertTrue(repoFile(destinationRepo, "plugins/org.eclipse.core.runtime_3.4.0.v20080512.jar").exists());
    }

    @Test
    public void testMirrorLatestOnly() throws Exception {
        MirrorOptions mirrorOptions = new MirrorOptions();
        mirrorOptions.setLatestVersionOnly(true);
        subject.mirrorStandalone(sourceRepos("e342", "e352"), destinationRepo, null, mirrorOptions, targetFolder);
        File[] runtimeBundles = new File(destinationRepo.getLocation(), "plugins").listFiles(new FileFilter() {

            public boolean accept(File file) {
                return file.getName().startsWith("org.eclipse.core.runtime");
            }
        });
        assertEquals(1, runtimeBundles.length);
    }

    @Test(expected = FacadeException.class)
    public void testMirrorNotExisting() throws Exception {
        subject.mirrorStandalone(e342PlusFragmentsRepo(), destinationRepo,
                Collections.singletonList(new IUDescription("org.eclipse.core.runtime", "10.0.0")),
                new MirrorOptions(), targetFolder);
    }

    @Test
    public void testMirrorAppendAlreadyExisting() throws Exception {
        mirrorCoreRuntimeBundle();
        assertEquals(1, getMirroredBundleFiles().length);
        // mirroring the same content again must not fail
        mirrorCoreRuntimeBundle();
        assertEquals(1, getMirroredBundleFiles().length);
    }

    private RepositoryReferences e342PlusFragmentsRepo() {
        return sourceRepos("e342", "fragments");
    }
}
