package com.apollographql.federation.springexample;

import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaseAppTest {
    public void lookupPlanckProduct(GraphQLTestTemplate graphqlTestTemplate) throws IOException {
        final GraphQLResponse response = graphqlTestTemplate.postForResource("queries/LookupPlanckProduct.graphql");
        assertNotNull(response, "response should not have been null");
        assertTrue(response.isOk(), "response should have been HTTP 200 OK");
        response.assertThatNoErrorsArePresent();
        final int quantity = response.get("$.data._entities[0].quantity", Integer.class);
        assertEquals(8658, quantity, "Inventory contains 8658 Plancks");
        final String federatedTrace = response.get("$.extensions.ftv1", String.class);
        assertNotNull(federatedTrace, "response should not have had null federated trace");
    }
}
