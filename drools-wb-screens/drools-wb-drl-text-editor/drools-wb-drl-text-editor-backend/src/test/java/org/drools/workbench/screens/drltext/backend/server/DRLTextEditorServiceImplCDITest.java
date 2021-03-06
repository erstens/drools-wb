/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.drltext.backend.server;

import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.drools.workbench.screens.drltext.model.DrlModelContent;
import org.drools.workbench.screens.drltext.service.DRLTextEditorService;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.guvnor.test.CDITestSetup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.backend.vfs.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DRLTextEditorServiceImplCDITest extends CDITestSetup {

    private static final String UNEMPLOY_ROOT = "DslSentencesInDrlFile/src/main/resources/org/kiegroup/";
    private static final String UNEMPLOY = UNEMPLOY_ROOT + "unemploy.dslr";
    private static final String UNEMPLOY_BROKEN = UNEMPLOY_ROOT + "unemploy-invalid.dslr";
    private static final String UNEMPLOY_REPLACE = UNEMPLOY_ROOT + "unemployAndReplace.dslr";

    private static final String CAR_DRIVING_ROOT = "drl/src/main/resources/org/kiegroup/";
    private static final String CAR_DRIVING_LICENSE = CAR_DRIVING_ROOT + "applyForCarDrivingLicense.drl";
    private static final String CAR_DRIVING_LICENSE_BROKEN = CAR_DRIVING_ROOT + "applyForCarDrivingLicenseWrongConstructor.drl";
    private static final String CAR_BUS_DRIVING_LICENSE = CAR_DRIVING_ROOT + "applyForCarAndBusDrivingLicense.drl";
    private static final String CAR_DRIVING_LICENSE_GLOBAL = CAR_DRIVING_ROOT + "applyForCarDrivingLicenseAndStore.drl";
    private static final String CAR_DRIVING_LICENSE_GLOBAL_BROKEN = CAR_DRIVING_ROOT + "applyForCarDrivingLicenseAndStoreBroken.drl";
    private static final String CAR_DRIVING_LICENSE_IMPORT = CAR_DRIVING_ROOT + "addAdditionalStorage.drl";
    private static final String CAR_DRIVING_LICENSE_IMPORT_BROKEN = CAR_DRIVING_ROOT + "addAdditionalStorageBroken.drl";

    private List<ValidationMessage> validationMessages;
    private DRLTextEditorService drlService;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        drlService = getReference(DRLTextEditorService.class);
    }

    @After
    public void tearDown() throws Exception {
        super.cleanup();
    }

    @Test
    public void testValidDSRLFile() throws Exception {
        validateResource(UNEMPLOY);

        assertEquals(0, validationMessages.size());
    }

    @Test
    public void testDSLCompinedWithPureDRL() throws Exception {
        validateResource(UNEMPLOY_REPLACE);

        assertEquals(0, validationMessages.size());
    }

    @Test
    public void testInvalidDSRLFile() throws Exception {
        validateResource(UNEMPLOY_BROKEN);

        assertEquals(3, validationMessages.size());
        assertTrue(validationMessages.get(0).getText().contains("Unable to expand: a"));
        assertTrue(validationMessages.get(1).getText().contains("Unable to expand:     b"));
        assertTrue(validationMessages.get(2).getText().contains("mismatched input 'then'"));
    }

    @Test
    public void testValidDRLFile() throws Exception {
        validateResource(CAR_DRIVING_LICENSE);

        assertEquals(0, validationMessages.size());
    }

    @Test
    public void testDRLFileWithGlobalVariable() throws Exception {
        validateResource(CAR_DRIVING_LICENSE_GLOBAL);

        assertEquals(0, validationMessages.size());
    }

    @Test
    public void testDRLFileWithUnknownGlobalVariable() throws Exception {
        validateResource(CAR_DRIVING_LICENSE_GLOBAL_BROKEN);

        Assertions.assertThat(validationMessages).hasSize(2);
        Assertions.assertThat(validationMessages)
                .allMatch(message -> message.getText()
                        .contains("Error: unable to resolve method using strict-mode: org.drools.core.spi.KnowledgeHelper.unknownStorageVariable()"));
    }

    @Test
    public void testDRLFileWithExplicitImport() throws Exception {
        validateResource(CAR_DRIVING_LICENSE_IMPORT);

        Assertions.assertThat(validationMessages).hasSize(0);
    }

    @Test
    public void testDRLFileWithExplicitNonExistingImport() throws Exception {
        validateResource(CAR_DRIVING_LICENSE_IMPORT_BROKEN);

        Assertions.assertThat(validationMessages).hasSize(2);
        Assertions.assertThat(validationMessages)
                .allMatch(message -> message.getText()
                        .contains("Error importing : 'org.kiegroup.storage.NonExistingCache'"));
    }

    @Test
    public void testValidDRLFileWithTwoRules() throws Exception {
        validateResource(CAR_BUS_DRIVING_LICENSE);

        assertEquals(0, validationMessages.size());
    }

    @Test
    public void testDRLFileWrongConstructor() throws Exception {
        validateResource(CAR_DRIVING_LICENSE_BROKEN);

        Assertions.assertThat(validationMessages).hasSize(2);
        Assertions.assertThat(validationMessages)
                .allMatch(message -> message.getText()
                        .contains("Unable to Analyse Expression drools.insert(new DrivingLicenseApplication(\"car\"))"));
    }

    @Test
    public void testLoadContent() throws Exception {
        final DrlModelContent content = drlService.loadContent(getPath(CAR_DRIVING_LICENSE));

        Assertions.assertThat(content.getDrl()).isEqualTo(drlService.load(getPath(CAR_DRIVING_LICENSE)));
        Assertions.assertThat(content.getFullyQualifiedClassNames()).hasSize(3);
        Assertions.assertThat(content.getFullyQualifiedClassNames())
                .contains("org.kiegroup.Person",
                          "org.kiegroup.DrivingLicenseApplication",
                          "org.kiegroup.storage.Storage");
    }

    @Test
    public void testLoadClassFields() throws Exception {
        final List<String> fields = drlService.loadClassFields(getPath(CAR_DRIVING_LICENSE),
                                                               "org.kiegroup.Person");

        Assertions.assertThat(fields).hasSize(3);
        Assertions.assertThat(fields).contains("this", "age", "dummy");
    }

    private Path getPath(final String resource) throws Exception {
        final URL resourceURL = getClass().getResource(resource);
        final org.uberfire.java.nio.file.Path resourceNioPath = fileSystemProvider.getPath(resourceURL.toURI());
        return Paths.convert(resourceNioPath);
    }

    private void validateResource(final String resource) throws Exception {
        final URL resourceURL = getClass().getResource(resource);
        validationMessages = drlService.validate(getPath(resource),
                                                 IOUtils.toString(resourceURL.toURI(), "UTF-8"));
    }
}
