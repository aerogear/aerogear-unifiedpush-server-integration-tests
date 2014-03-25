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

import java.io.File;

import org.jboss.aerogear.unifiedpush.utils.Constants;
import org.jboss.aerogear.unifiedpush.utils.SenderStatisticsEndpoint;
import org.jboss.aerogear.unifiedpush.utils.ServerSocketUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.ApnsService;
import com.notnoop.apns.ApnsServiceBuilder;
import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.apns.PayloadBuilder;
import com.notnoop.apns.internal.ApnsServiceImpl;
import com.notnoop.exceptions.NetworkIOException;
import org.jboss.aerogear.test.api.sender.SenderStatistics;
import org.jboss.aerogear.unifiedpush.utils.Constants;
import org.jboss.aerogear.unifiedpush.utils.SenderStatisticsEndpoint;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter;

import java.io.File;

public final class Deployments {

    private static final String DEFAULT_STAGING_UPS_VERSION = "0.10.2";

    private static final String UPS_STAGING_URL = "http://people.apache.org/~matzew/aerogear-staging/";

    private Deployments() {
        throw new UnsupportedOperationException("No instantiation.");
    }

    public static WebArchive unifiedPushServer() {

        // in case we want to download it from staing repo
        if (System.getProperty("upsStaging") != null && System.getProperty("upsStaging").equals("true")) {
            System.out.print("Resolving UPS from staging repo");
            return Deployments.addCustomPersistence(Deployments.getUPSFromStaging());
        }

        final String unifiedPushServerPom = System.getProperty("unified.push.server.location",
                "aerogear-unifiedpush-server/server/pom.xml");

        WebArchive war = ShrinkWrap.create(MavenImporter.class).loadPomFromFile(unifiedPushServerPom)
                .importBuildOutput()
                .as(WebArchive.class);

        return Deployments.addCustomPersistence(war);
    }

    public static WebArchive unifiedPushServerWithClasses(Class<?>... clazz) {

        WebArchive war = Deployments.unifiedPushServer();

        war.addClasses(clazz);

        File[] libs = Maven.resolver().loadPomFromFile("pom.xml").resolve("org.mockito:mockito-core").withTransitivity()
                .asFile();
        war = war.addAsLibraries(libs);

        return war;
    }

    public static WebArchive customUnifiedPushServerWithClasses(Class<?>... clazz) {

        WebArchive war = Deployments.unifiedPushServer();
        war = Deployments.addCustomPersistence(war);

        war.delete("/WEB-INF/lib/gcm-server-1.0.2.jar");

        war.addClass(SenderStatisticsEndpoint.class, SenderStatistics.class);

        war.addClasses(clazz);

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "gcm-server-1.0.2.jar").addClasses(Result.class,
                Message.class,
                MulticastResult.class, Message.class, Sender.class);
        war.addAsLibraries(jar);

        war.delete("/WEB-INF/lib/apns-0.2.3.jar");

        JavaArchive apnsJar = ShrinkWrap.create(JavaArchive.class, "apns-0.2.3.jar").addClasses(NetworkIOException
                        .class,
                ApnsService.class, ApnsServiceImpl.class, ApnsServiceBuilder.class, PayloadBuilder.class, APNS.class,
                Constants.class, ApnsNotification.class, EnhancedApnsNotification.class);
        war.addAsLibraries(apnsJar);

        PomEquippedResolveStage resolver = Maven.resolver().loadPomFromFile("pom.xml");

        // here we resolve mockito transitively, other artifact without transitivity
        File[] libs = resolver.resolve("com.jayway.restassured:rest-assured", "com.jayway.awaitility:awaitility")
                .withoutTransitivity().asFile();
        war.addAsLibraries(libs);
        libs = resolver.resolve("org.mockito:mockito-core").withTransitivity().asFile();
        war = war.addAsLibraries(libs);

        return war;
    }

    private static WebArchive addCustomPersistence(WebArchive webArchive) {
        // replace original persistence.xml with testing one
        webArchive.delete("/WEB-INF/classes/META-INF/persistence.xml");
        // testing persistence
        webArchive.addAsResource("META-INF/persistence.xml");
        return webArchive;
    }

    /**
     * Gets UPS from staging repo of specified version
     *
     * When some argument is null or empty String, defaults are used.
     *
     * @param version version of UPS you want to get
     * @return UPS of specified {@code version}
     */
    public static WebArchive getUPSFromStaging(String version) {

        if (version == null || version.isEmpty()) {
            version = DEFAULT_STAGING_UPS_VERSION;
        }

        // https://issues.jboss.org/browse/WFK2-61
        File warFile = Maven.configureResolver()
            .withRemoteRepo(MavenRemoteRepositories.createRemoteRepository("staging_ups", UPS_STAGING_URL, "default"))
            .withMavenCentralRepo(false)
            .resolve("org.jboss.aerogear.unifiedpush:unifiedpush-server:war:" + version)
            .withoutTransitivity().asSingle(File.class);

        WebArchive finalArchive = ShrinkWrap.create(ZipImporter.class, "ag-push.war").importFrom(warFile).as(WebArchive.class);

        return finalArchive;
    }

    public static WebArchive getUPSFromStaging() {
        return getUPSFromStaging(DEFAULT_STAGING_UPS_VERSION);
    }
}
