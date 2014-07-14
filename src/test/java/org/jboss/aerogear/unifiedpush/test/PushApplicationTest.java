/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.DecoderConfig;
import com.jayway.restassured.config.EncoderConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import org.apache.http.HttpStatus;
import org.jboss.aerogear.test.ContentTypes;
import org.jboss.aerogear.test.Helper;
import org.jboss.aerogear.test.Session;
import org.jboss.aerogear.test.api.ModelAsserts;
import org.jboss.aerogear.test.api.application.PushApplicationBlueprint;
import org.jboss.aerogear.test.api.application.PushApplicationContext;
import org.jboss.aerogear.test.api.application.PushApplicationWorker;
import org.jboss.aerogear.test.model.PushApplication;
import org.jboss.aerogear.unifiedpush.utils.CheckingExpectedException;
import org.jboss.aerogear.unifiedpush.utils.Constants;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.aerogear.arquillian.junit.ArquillianRule;
import org.jboss.aerogear.arquillian.junit.ArquillianRules;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(ArquillianRules.class)
public class PushApplicationTest {

    private static final String TOO_LONG_NAME = Helper.randomStringOfLength(256);
    private static final String TOO_LONG_DESCRIPTION = Helper.randomStringOfLength(256);

    @ArquillianRule
    public static UnifiedPushServer ups = new UnifiedPushServer() {
        @Override
        protected UnifiedPushServer setup() {
            return this;
        }
    };

    @Rule
    public CheckingExpectedException thrown = CheckingExpectedException.none();

    @After
    public void cleanPushApplications() {
        ups.with(PushApplicationWorker.worker()).findAll().removeAll();
    }

    @BeforeClass
    public static void setup() {
        RestAssured.config = RestAssuredConfig.newConfig()
                .decoderConfig(DecoderConfig.decoderConfig().defaultContentCharset("UTF-8"))
                .encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset("UTF-8"));

        RestAssured.keystore(Constants.KEYSTORE_PATH, Constants.KEYSTORE_PASSWORD);
    }

    @AfterClass
    public static void cleanup() {
        RestAssured.config = RestAssuredConfig.newConfig()
                .decoderConfig(DecoderConfig.decoderConfig().defaultContentCharset("ISO-8859-1"))
                .encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset("ISO-8859-1"));
    }

    @Deployment(name = Deployments.AUTH_SERVER, testable = false, order = 1)
    @TargetsContainer("main-server-group")
    public static WebArchive createAuthServerDeployment() {
        return Deployments.authServer();
    }

    @Deployment(name = Deployments.AG_PUSH, testable = false, order = 2)
    @TargetsContainer("main-server-group")
    public static WebArchive createDeployment() {
        return Deployments.unifiedPushServer();
    }

    @Test
    public void testCRUD() {
        performCRUD(PushApplicationWorker.worker());
    }

    @Test
    public void testCRUDUTF8() {
        performCRUD(PushApplicationWorker.worker().contentType(ContentTypes.jsonUTF8()));
    }

    @Test
    public void testRegisterWithTooLongName() {
        thrown.expectUnexpectedResponseException(HttpStatus.SC_BAD_REQUEST);
        ups.with(PushApplicationWorker.worker()).generate().name(TOO_LONG_NAME).persist();
    }

    @Test
    public void testRegisterWithTooLongDescription() {
        thrown.expectUnexpectedResponseException(HttpStatus.SC_BAD_REQUEST);
        ups.with(PushApplicationWorker.worker()).generate().description(TOO_LONG_DESCRIPTION).persist();
    }

    @Test
    public void testReadNonexistentApplication() {
        thrown.expectUnexpectedResponseException(HttpStatus.SC_NOT_FOUND);

        ups.with(PushApplicationWorker.worker()).find(UUID.randomUUID().toString());
    }

    @Test
    public void testUpdateWithTooLongName() {
        PushApplication application = ups.with(PushApplicationWorker.worker()).generate().persist().detachEntity();

        thrown.expectUnexpectedResponseException(HttpStatus.SC_BAD_REQUEST);

        ups.with(PushApplicationWorker.worker()).edit(application.getPushApplicationID()).name(TOO_LONG_NAME).merge();
    }

    @Test
    public void testUpdateWithTooLongDescription() {
        PushApplication application = ups.with(PushApplicationWorker.worker()).generate().persist().detachEntity();

        thrown.expectUnexpectedResponseException(HttpStatus.SC_BAD_REQUEST);

        ups.with(PushApplicationWorker.worker())
                .edit(application.getPushApplicationID()).description(TOO_LONG_DESCRIPTION).merge();
    }

    @Test
    public void testUpdateNonexistentApplication() {
        PushApplication pushApplication = new PushApplication();
        pushApplication.setPushApplicationID(UUID.randomUUID().toString());

        thrown.expectUnexpectedResponseException(HttpStatus.SC_NOT_FOUND);

        ups.with(PushApplicationWorker.worker()).merge(pushApplication);
    }

    @Test
    public void testResetMasterSecret() {
        PushApplication application = ups.with(PushApplicationWorker.worker()).generate().persist().detachEntity();

        ups.with(PushApplicationWorker.worker()).resetMasterSecret(application.getPushApplicationID());

        PushApplication changedApplication = ups.with(PushApplicationWorker.worker())
                .find(application.getPushApplicationID()).detachEntity();

        assertThat(changedApplication.getPushApplicationID(), is(application.getPushApplicationID()));
        assertThat(changedApplication.getMasterSecret(), is(not(application.getMasterSecret())));

        ups.with(PushApplicationWorker.worker()).removeById(application.getPushApplicationID());
    }

    @Test
    public void testResetMasterSecretNonexistentApplication() {
        thrown.expectUnexpectedResponseException(HttpStatus.SC_NOT_FOUND);

        ups.with(PushApplicationWorker.worker()).resetMasterSecret(UUID.randomUUID().toString());
    }

    @Test
    public void testRegistrationWithoutAuthorization() {
        thrown.expectUnexpectedResponseException(HttpStatus.SC_UNAUTHORIZED);

        Session invalidSession = Session.newSession(ups.getSession().getBaseUrl().toExternalForm());

        PushApplicationWorker.worker().createContext(invalidSession, null).generate().persist();
    }

    @Test
    public void testDeleteNonexistentApplication() {
        thrown.expectUnexpectedResponseException(HttpStatus.SC_NOT_FOUND);

        ups.with(PushApplicationWorker.worker()).removeById(UUID.randomUUID().toString());

    }

    private void performCRUD(PushApplicationWorker worker) {
        // CREATE
        List<PushApplication> persistedApplications = ups.with(worker)
                .generate().name("AwesomeAppěščřžýáíéňľ").persist()
                .generate().name("AwesomeAppவான்வழிe").persist()
                .detachEntities();

        assertThat(persistedApplications, is(notNullValue()));
        assertThat(persistedApplications.size(), is(2));

        PushApplication persistedApplication = persistedApplications.get(0);
        PushApplication persistedApplication1 = persistedApplications.get(1);

        // READ
        PushApplicationContext context = ups.with(worker).findAll();
        List<PushApplication> readApplications = context.detachEntities();
        assertThat(readApplications, is(notNullValue()));
        assertThat(readApplications.size(), is(2));

        ModelAsserts.assertModelsEqual(persistedApplication,
                context.detachEntity(persistedApplication.getPushApplicationID()));
        ModelAsserts.assertModelsEqual(persistedApplication1,
                context.detachEntity(persistedApplication1.getPushApplicationID()));

        // UPDATE
        ups.with(worker)
                .edit(persistedApplication.getPushApplicationID()).name("newname").description("newdescription").merge();
        PushApplication readApplication = ups.with(worker)
                .find(persistedApplication.getPushApplicationID())
                .detachEntity();
        assertThat(readApplication.getName(), is("newname"));
        assertThat(readApplication.getDescription(), is("newdescription"));

        // DELETE
        readApplications = ups.with(worker)
                .removeById(persistedApplication.getPushApplicationID())
                .removeById(persistedApplication1.getPushApplicationID())
                .findAll()
                .detachEntities();
        assertThat(readApplications.size(), is(0));
    }


}