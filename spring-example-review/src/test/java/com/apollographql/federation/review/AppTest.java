package com.apollographql.federation.review;

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
    public void lookupReview() throws IOException {
        final GraphQLResponse response = graphqlTestTemplate.postForResource("queries/TestReview.gql");
        assertNotNull(response, "response should not have been null");
        assertTrue(response.isOk(), "response should have been OK");
        final int reviews_Id = response.get("$.data._entities[0].reviews[0].id", Integer.class);
        assertEquals(3, reviews_Id, "reviews_Id contains 2");
    }
}
