package io.gqljf.federation;

import graphql.ExecutionResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FederatedSchemaBuilderQueryEntityTest extends AbstractTest {

    @Test
    void testQueryLongDummy() throws Exception {
        List<FederatedEntityResolver<?, ?>> entityResolvers = List.of(
                new FederatedEntityResolver<Long, LongEntityDummy>("LongEntityDummy", id -> new LongEntityDummy(id, "qwerty")) {
                }
        );

        GraphQLSchema transformed = new FederatedSchemaBuilder()
                .schemaInputStream(getResourceAsStream("entity-schema.graphqls"))
                .runtimeWiring(RuntimeWiring.newRuntimeWiring().build())
                .federatedEntitiesResolvers(entityResolvers)
                .build();

        Map<String, Object> variables = Map.of("_representations", Map.of("__typename", "LongEntityDummy", "id", "1019"));
        ExecutionResult executionResult = execute(transformed, getResourceAsString("query/long-entity-query"), variables);

        Map<String, List<Map<String, Object>>> data = executionResult.getData();
        assertNotNull(data);

        Map<String, Object> queryResult = data.get("_entities").get(0);
        assertNotNull(queryResult);

        assertEquals(1019L, queryResult.get("id"));
        assertEquals("qwerty", queryResult.get("field"));
    }

    @Test
    void testQueryStringDummy() throws Exception {
        List<FederatedEntityResolver<?, ?>> entityResolvers = List.of(
                new FederatedEntityResolver<String, StringEntityDummy>("StringEntityDummy", id -> new StringEntityDummy(id, "101")) {
                }
        );

        GraphQLSchema transformed = new FederatedSchemaBuilder()
                .schemaInputStream(getResourceAsStream("entity-schema.graphqls"))
                .runtimeWiring(RuntimeWiring.newRuntimeWiring().build())
                .federatedEntitiesResolvers(entityResolvers)
                .build();

        Map<String, Object> variables = Map.of("_representations", Map.of("__typename", "StringEntityDummy", "id", "30"));
        ExecutionResult executionResult = execute(transformed, getResourceAsString("query/string-entity-query"), variables);

        Map<String, List<Map<String, Object>>> data = executionResult.getData();
        assertNotNull(data);

        Map<String, Object> queryResult = data.get("_entities").get(0);
        assertNotNull(queryResult);

        assertEquals("30", queryResult.get("id"));
        assertEquals("101", queryResult.get("field"));
    }

    private static class LongEntityDummy {
        public LongEntityDummy(long id, String field) {
            this.id = id;
            this.field = field;
        }

        Long id;
        String field;
    }

    private static class StringEntityDummy {
        public StringEntityDummy(String id, String field) {
            this.id = id;
            this.field = field;
        }

        String id;
        String field;
    }
}