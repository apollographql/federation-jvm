package com.apollographql.federation.springexample;

import com.graphql.spring.boot.test.GraphQLTest;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@GraphQLTest(profiles = "graphql-java")
@Import(App.class)
public class GraphQLJavaAppTest extends BaseAppTest {
  @Autowired private GraphQLTestTemplate graphqlTestTemplate;

  @Test
  public void lookupPlanckProduct() throws IOException {
    lookupPlanckProduct(graphqlTestTemplate);
  }
}
