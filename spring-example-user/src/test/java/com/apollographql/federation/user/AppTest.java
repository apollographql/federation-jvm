package com.apollographql.federation.user;

import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTest;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@GraphQLTest
@Import({TestUserConfiguration.class, GraphQLConfig.class})
public class AppTest {

    @Autowired
    private GraphQLTestTemplate graphqlTestTemplate;

    @Test
    public void lookupPlanckProduct() throws IOException {
        final GraphQLResponse response = graphqlTestTemplate.postForResource("queries/TestUser.gql");
        assertNotNull(response, "response should not have been null");
        assertTrue(response.isOk(), "response should have been OK");
        final String name = response.get("$.data._entities[0].name", String.class);
        assertEquals("@complete", name, "User name is @complete");
    }
}
