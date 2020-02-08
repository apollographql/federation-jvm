package io.gqljf.federation;

import graphql.ExecutionResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FederatedSchemaBuilderQueryTest extends AbstractTest {

    @Test
    void testQuery() throws Exception {
        DataFetcher<Dummy> dummyDataFetcher = env -> new Dummy(7L, "fieldValue");

        GraphQLSchema transformed = new FederatedSchemaBuilder()
                .schemaInputStream(getResourceAsStream("base-schema.graphqls"))
                .runtimeWiring(RuntimeWiring.newRuntimeWiring()
                        .type("Query", builder -> builder.dataFetcher("getSomeObject", dummyDataFetcher))
                        .build())
                .build();

        ExecutionResult executionResult = execute(transformed, "{ getSomeObject(id: 3) { id field } }");

        Map<String, Map<String, Object>> data = executionResult.getData();
        assertNotNull(data);

        Map<String, Object> queryResult = data.get("getSomeObject");
        assertNotNull(queryResult);

        assertEquals(7, queryResult.get("id"));
        assertEquals("fieldValue", queryResult.get("field"));
    }

    private static class Dummy {
        public Dummy(Long id, String field) {
            this.id = id;
            this.field = field;
        }

        Long id;
        String field;
    }
}