package com.apollographql.federation.user;

import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTest;
import com.graphql.spring.boot.test.GraphQLTestAutoConfiguration;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import com.oembedler.moon.graphql.boot.GraphQLJavaToolsAutoConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@GraphQLTest
@SpringBootTest
@ImportAutoConfiguration(classes = {GraphQLJavaToolsAutoConfiguration.class,
        JacksonAutoConfiguration.class, GraphQLTestAutoConfiguration.class})
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
