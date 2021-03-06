/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.test.api.application;

import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.jboss.aerogear.test.*;
import org.jboss.aerogear.test.api.AbstractUPSWorker;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PushApplicationWorker extends AbstractUPSWorker<PushApplication, String, PushApplicationBlueprint, PushApplicationEditor, Void, PushApplicationContext, PushApplicationWorker> {

    private String contentType = Utilities.ContentTypes.json();

    private PushApplicationWorker() {

    }

    @Override
    public PushApplicationContext createContext(Session session, Void parent) {
        return new PushApplicationContext(this, session);
    }

    @Override
    public JSONObject marshall(PushApplication application) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", application.getName());
        jsonObject.put("description", application.getDescription());
        return jsonObject;
    }

    @Override
    public PushApplicationEditor demarshall(PushApplicationContext context, JsonPath jsonPath) {
        PushApplicationEditor application = new PushApplicationEditor(context);
        application.setName(jsonPath.getString("name"));
        application.setDescription(jsonPath.getString("description"));
        application.setPushApplicationID(jsonPath.getString("pushApplicationID"));
        application.setMasterSecret(jsonPath.getString("masterSecret"));
        application.setDeveloper(jsonPath.getString("developer"));
        return application;
    }

    @Override
    public List<PushApplicationEditor> create(PushApplicationContext context, Collection<? extends
            PushApplicationBlueprint> pushApplications) {
        List<PushApplicationEditor> registeredApplications = new ArrayList<PushApplicationEditor>();
        for (PushApplication pushApplication : pushApplications) {
            Response response = context.getSession().givenAuthorized()
                    .contentType(contentType)
                    .header(Utilities.Headers.acceptJson())
                    .body(marshall(pushApplication))
                    .post("/rest/applications");

            UnexpectedResponseException.verifyResponse(response, HttpStatus.SC_CREATED);

            registeredApplications.add(demarshall(context, response.jsonPath()));
        }
        return registeredApplications;
    }

    @Override
    public List<PushApplicationEditor> readAll(PushApplicationContext context) {

        Response response = context.getSession().givenAuthorized()
                .contentType(Utilities.ContentTypes.json())
                .header(Utilities.Headers.acceptJson())
                .get("/rest/applications");

        UnexpectedResponseException.verifyResponse(response, HttpStatus.SC_OK);

        List<PushApplicationEditor> applications = new ArrayList<PushApplicationEditor>();

        JsonPath jsonPath = response.jsonPath();

        List<Map<String, ?>> items = jsonPath.getList("");

        for (int i = 0; i < items.size(); i++) {
            jsonPath.setRoot("[" + i + "]");

            // FIXME this might not be the right implementation
            // it can actually leak values of previous implementation
            PushApplicationEditor pushApplication = demarshall(context, jsonPath);
            applications.add(pushApplication);
        }

        return applications;
    }

    @Override
    public PushApplicationEditor read(PushApplicationContext context, String id) {
        Response response = context.getSession().givenAuthorized()
                .contentType(contentType)
                .header(Utilities.Headers.acceptJson())
                .get("/rest/applications/{pushApplicationID}", id);

        UnexpectedResponseException.verifyResponse(response, HttpStatus.SC_OK);

        return demarshall(context, response.jsonPath());
    }

    @Override
    public void update(PushApplicationContext context, Collection<? extends PushApplication> pushApplications) {
        for (PushApplication pushApplication : pushApplications) {
            Response response = context.getSession().givenAuthorized()
                    .contentType(contentType)
                    .header(Utilities.Headers.acceptJson())
                    .body(marshall(pushApplication))
                    .put("/rest/applications/{pushApplicationID}", context.getEntityID(pushApplication));

            UnexpectedResponseException.verifyResponse(response, HttpStatus.SC_NO_CONTENT);

            // FIXME do we need to demarshall?
        }
    }

    @Override
    public void deleteById(PushApplicationContext context, String id) {
        Response response = context.getSession().givenAuthorized()
                .contentType(contentType)
                .header(Utilities.Headers.acceptJson())
                .delete("/rest/applications/{pushApplicationID}", id);

        UnexpectedResponseException.verifyResponse(response, HttpStatus.SC_NO_CONTENT);
    }

    public void resetMasterSecret(PushApplicationContext context, String id) {
        Response response = context.getSession().givenAuthorized()
                .contentType(contentType)
                .header(Utilities.Headers.acceptJson())
                .body("[]")
                .put("/rest/applications/{pushApplicationID}/reset", id);

        UnexpectedResponseException.verifyResponse(response, HttpStatus.SC_OK);

        // FIXME should we need to demarshall
    }

    public PushApplicationWorker contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public static PushApplicationWorker worker() {
        return new PushApplicationWorker();
    }


}