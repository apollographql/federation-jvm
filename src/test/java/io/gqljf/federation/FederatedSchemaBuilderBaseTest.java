package io.gqljf.federation;

import graphql.ExecutionResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import io.gqljf.federation.misc.FederationException;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

class FederatedSchemaBuilderBaseTest extends AbstractTest {

    // subscription definition after graphql-java transformation
    private final String subscriptionDefinition = "type Subscription {\n" +
            "  subscribe: Boolean!\n" +
            "}";

    @Test
    void testSchemaTransformation() throws Exception {
        GraphQLSchema transformed = new FederatedSchemaBuilder()
                .schemaInputStream(getResourceAsStream("base-schema.graphqls"))
                .runtimeWiring(RuntimeWiring.newRuntimeWiring().build())
                .build();

        ExecutionResult executionResult = execute(transformed, "{_service{sdl}}");

        Map<String, Map<String, String>> data = executionResult.getData();
        assertNotNull(data);

        Map<String, String> service = data.get("_service");
        assertNotNull(service);

        String sdl = service.get("sdl");
        assertNotNull(sdl);
        assertThat(sdl, new StringContains("getSomeObject(id: Int!): Dummy!"));
        assertThat(sdl, new StringContains("testMutation(input: Input!): Int!"));
        assertThat(sdl, new StringContains(subscriptionDefinition));
    }

    // Now Apollo Server doesn't support subscriptions
    @Test
    void testSdlShouldNotContainSubscriptionType() throws Exception {
        GraphQLSchema transformed = new FederatedSchemaBuilder()
                .schemaInputStream(getResourceAsStream("base-schema.graphqls"))
                .runtimeWiring(RuntimeWiring.newRuntimeWiring().build())
                .excludeSubscriptionsFromApolloSdl(true)
                .build();

        ExecutionResult executionResult = execute(transformed, "{_service{sdl}}");

        Map<String, Map<String, String>> data = executionResult.getData();
        assertNotNull(data);

        Map<String, String> service = data.get("_service");
        assertNotNull(service);

        String sdl = service.get("sdl");
        assertNotNull(sdl);
        assertThat(sdl, not(new StringContains(subscriptionDefinition)));
    }

    @Test
    void testShouldThrowExceptionWithoutSchema() {
        FederationException exception = assertThrows(FederationException.class, () -> {
            new FederatedSchemaBuilder()
                    .runtimeWiring(RuntimeWiring.newRuntimeWiring().build())
                    .build();
        });

        assertEquals("SDL should not be null", exception.getMessage());
    }

    @Test
    void testShouldThrowExceptionWithoutRuntimeWiring() {
        FederationException exception = assertThrows(FederationException.class, () -> {
            new FederatedSchemaBuilder()
                    .schemaInputStream(getResourceAsStream("base-schema.graphqls"))
                    .build();
        });

        assertEquals("RuntimeWiring should not be null", exception.getMessage());
    }
}