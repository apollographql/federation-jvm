package com.apollographql.federation.springexample;

import com.graphql.spring.boot.test.GraphQLTest;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@RunWith(SpringRunner.class)
@GraphQLTest(profiles = "graphql-java-tools")
@Import(App.class)
public class GraphQLJavaToolsAppTest extends BaseAppTest {
    @Autowired
    private GraphQLTestTemplate graphqlTestTemplate;

    @Test
    public void lookupPlanckProduct() throws IOException {
        lookupPlanckProduct(graphqlTestTemplate);
    }
}
