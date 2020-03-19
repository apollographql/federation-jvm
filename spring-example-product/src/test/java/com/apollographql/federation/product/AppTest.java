package com.apollographql.federation.product;

import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTest;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@GraphQLTest
public class AppTest {
    @Autowired
    private GraphQLTestTemplate graphqlTestTemplate;

    @Test
    public void lookupPlanckProduct() throws IOException {
        final GraphQLResponse response = graphqlTestTemplate.postForResource("queries/LookupProduct.gql");
        assertNotNull(response, "response should not have been null");
        assertTrue(response.isOk(), "response should have been OK");
        final int weight = response.get("$.data._entities[0].weight", Integer.class);
        assertEquals(100, weight, "Inventory contains 8658 Plancks");
    }
}
