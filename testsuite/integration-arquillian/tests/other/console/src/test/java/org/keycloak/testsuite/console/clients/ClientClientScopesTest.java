/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.testsuite.console.clients;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.arquillian.graphene.page.Page;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.testsuite.Assert;
import org.keycloak.testsuite.console.page.clients.clientscopes.ClientScopesEvaluate;
import org.keycloak.testsuite.console.page.clients.clientscopes.ClientScopesEvaluateForm;
import org.keycloak.testsuite.console.page.clients.clientscopes.ClientScopesSetup;
import org.keycloak.testsuite.console.page.clients.clientscopes.ClientScopesSetupForm;
import org.keycloak.util.JsonSerialization;
import org.keycloak.util.TokenUtil;

import static org.junit.Assert.assertNotNull;
import static org.keycloak.testsuite.auth.page.login.Login.OIDC;

/**
 * Test for the "Client Scopes" tab of client (Binding between "Client" and "Client Scopes")
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class ClientClientScopesTest extends AbstractClientTest {

    private ClientRepresentation newClient;
    private ClientRepresentation found;


    @Page
    private ClientScopesSetup clientScopesSetupPage;

    @Page
    private ClientScopesEvaluate clientScopesEvaluatePage;


    @Before
    public void before() {
        newClient = createClientRep(TEST_CLIENT_ID, OIDC);
        newClient.setFullScopeAllowed(false);

        testRealmResource().clients().create(newClient).close();

        found = findClientByClientId(TEST_CLIENT_ID);
        assertNotNull("Client " + TEST_CLIENT_ID + " was not found.", found);
        clientScopesSetupPage.setId(found.getId());
        clientScopesSetupPage.navigateTo();
    }


    @Test
    public void testSetupClientScopes() {
        ClientScopesSetupForm setupForm = clientScopesSetupPage.form();

        // Test the initial state
        Assert.assertNames(setupForm.getAvailableDefaultClientScopes());
        Assert.assertNames(setupForm.getDefaultClientScopes(), "email", "profile");
        Assert.assertNames(setupForm.getAvailableOptionalClientScopes());
        Assert.assertNames(setupForm.getOptionalClientScopes(), "address", "phone", "offline_access");

        // Remove 'profile' as default client scope and assert
        setupForm.setDefaultClientScopes(Collections.singletonList("email"));
        Assert.assertNames(setupForm.getAvailableDefaultClientScopes(), "profile");
        Assert.assertNames(setupForm.getDefaultClientScopes(), "email");
        Assert.assertNames(setupForm.getAvailableOptionalClientScopes(), "profile");
        Assert.assertNames(setupForm.getOptionalClientScopes(), "address", "phone", "offline_access");

        // Add 'profile' as optional client scope and assert
        setupForm.setOptionalClientScopes(Arrays.asList("profile", "address", "phone", "offline_access"));
        Assert.assertNames(setupForm.getAvailableDefaultClientScopes());
        Assert.assertNames(setupForm.getDefaultClientScopes(), "email");
        Assert.assertNames(setupForm.getAvailableOptionalClientScopes());
        Assert.assertNames(setupForm.getOptionalClientScopes(), "profile", "address", "phone", "offline_access");

        // Retrieve client through adminClient
        found = findClientByClientId(TEST_CLIENT_ID);
        Assert.assertNames(found.getDefaultClientScopes(), "email", "role_list"); // SAML client scope 'role_list' is included too in the rep
        Assert.assertNames(found.getOptionalClientScopes(), "profile", "address", "phone", "offline_access");


        // Revert and check things successfully reverted
        setupForm.setOptionalClientScopes(Arrays.asList("address", "phone", "offline_access"));
        Assert.assertNames(setupForm.getAvailableDefaultClientScopes(), "profile");
        setupForm.setDefaultClientScopes(Arrays.asList("profile", "email"));

        Assert.assertNames(setupForm.getAvailableDefaultClientScopes());
        Assert.assertNames(setupForm.getDefaultClientScopes(), "email", "profile");
        Assert.assertNames(setupForm.getAvailableOptionalClientScopes());
        Assert.assertNames(setupForm.getOptionalClientScopes(), "address", "phone", "offline_access");
    }


    @Test
    public void testEvaluateClientScopes() throws IOException {
        clientScopesEvaluatePage.setId(found.getId());
        clientScopesEvaluatePage.navigateTo();

        ClientScopesEvaluateForm evaluateForm = clientScopesEvaluatePage.form();

        // Check the defaults
        Assert.assertNames(evaluateForm.getAvailableClientScopes(), "address", "phone", "offline_access");
        Assert.assertNames(evaluateForm.getAssignedClientScopes());
        Assert.assertNames(evaluateForm.getEffectiveClientScopes(), "profile", "email");

        // Add some optional scopes to the evaluation
        evaluateForm.setAssignedClientScopes(Arrays.asList("address", "phone"));
        Assert.assertNames(evaluateForm.getAvailableClientScopes(), "offline_access");
        Assert.assertNames(evaluateForm.getAssignedClientScopes(), "address", "phone");
        Assert.assertNames(evaluateForm.getEffectiveClientScopes(), "address", "phone", "profile", "email");

        // Remove optional 'phone' scope from the evaluation
        evaluateForm.setAssignedClientScopes(Arrays.asList("address", "offline_access"));
        Assert.assertNames(evaluateForm.getAvailableClientScopes(), "phone");
        Assert.assertNames(evaluateForm.getAssignedClientScopes(), "address", "offline_access");
        Assert.assertNames(evaluateForm.getEffectiveClientScopes(), "address", "offline_access", "profile", "email");

        // Select some user
        evaluateForm.selectUser("test");

        // Submit
        evaluateForm.evaluate();

        // Test protocolMappers of 'address' , 'profile' and 'email' scopes are included
        Set<String> protocolMappers = evaluateForm.getEffectiveProtocolMapperNames();
        Assert.assertTrue(protocolMappers.contains("address"));
        Assert.assertTrue(protocolMappers.contains("email"));
        Assert.assertTrue(protocolMappers.contains("email verified"));
        Assert.assertTrue(protocolMappers.contains("username"));
        Assert.assertTrue(protocolMappers.contains("full name"));
        Assert.assertFalse(protocolMappers.contains("phone"));

        // Test roles
        evaluateForm.showRoles();
        Assert.assertNames(evaluateForm.getGrantedRealmRoles(), "offline_access");
        Assert.assertNames(evaluateForm.getNotGrantedRealmRoles(), "uma_authorization");

        // Test access token
        evaluateForm.showToken();
        String accessTokenStr = evaluateForm.getAccessToken();

        AccessToken token = JsonSerialization.readValue(accessTokenStr, AccessToken.class);
        String scopeParam = token.getScope();
        Assert.assertTrue(TokenUtil.isOIDCRequest(scopeParam));
        Assert.assertTrue(TokenUtil.hasScope(scopeParam, "address"));
        Assert.assertTrue(TokenUtil.hasScope(scopeParam, "profile"));
        Assert.assertTrue(TokenUtil.hasScope(scopeParam, "email"));
        Assert.assertFalse(TokenUtil.hasScope(scopeParam, "phone"));
    }
}
