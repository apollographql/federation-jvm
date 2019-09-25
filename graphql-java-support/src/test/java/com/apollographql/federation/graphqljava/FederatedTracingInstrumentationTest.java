package com.apollographql.federation.graphqljava;

import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation;
import com.google.protobuf.InvalidProtocolBufferException;
import graphql.GraphQL;
import graphql.GraphQLException;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import mdg.engine.proto.Reports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederatedTracingInstrumentationTest {
    private final String tracingSDL = TestUtils.readResource("schemas/tracing.graphql");
    private GraphQL graphql;

    @BeforeEach
    void setupSchema() {
        TypeDefinitionRegistry typeDefs = new SchemaParser().parse(tracingSDL);
        RuntimeWiring resolvers = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder ->
                        // return two items
                        builder.dataFetcher("widgets", env -> {
                            ArrayList<Object> objects = new ArrayList<>(2);
                            objects.add(new Object());
                            objects.add(new Object());
                            return objects;
                        }).dataFetcher("listOfLists", env -> {
                            ArrayList<ArrayList<Object>> lists = new ArrayList<>(2);
                            lists.add(new ArrayList<>(2));
                            lists.add(new ArrayList<>(2));
                            lists.get(0).add(new Object());
                            lists.get(0).add(new Object());
                            lists.get(1).add(new Object());
                            lists.get(1).add(new Object());
                            return lists;
                        })
                                .dataFetcher("listOfScalars", env -> new String[]{"one", "two", "three"}))
                .type("Widget", builder ->
                        // Widget.foo works normally, Widget.bar always throws an error
                        builder.dataFetcher("foo", env -> "hello world")
                                .dataFetcher("bar", env -> {
                                    throw new GraphQLException("whoops");
                                }))
                .build();

        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefs, resolvers);
        graphql = GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(new FederatedTracingInstrumentation())
                .build();
    }

    @Test
    void testTracing() throws InvalidProtocolBufferException {
        Map<String, Object> result = graphql.execute("{ widgets { foo, baz: bar }, listOfLists { foo }, listOfScalars }").toSpecification();

        Object extensions = result.get("extensions");
        assertTrue(extensions instanceof Map);

        String ftv1 = ((Map) extensions).get("ftv1").toString();
        byte[] decoded = Base64.getDecoder().decode(ftv1);

        Reports.Trace trace = Reports.Trace.parseFrom(decoded);
        assertTrue(trace.getStartTime().getSeconds() > 0, "Start time has seconds");
        assertTrue(trace.getStartTime().getNanos() > 0, "Start time has nanoseconds");
        assertTrue(trace.getEndTime().getSeconds() > 0, "End time has seconds");
        assertTrue(trace.getEndTime().getNanos() > 0, "End time has nanoseconds");
        assertTrue(trace.getDurationNs() > 0, "DurationNs is greater than zero");
        assertEquals(3, trace.getRoot().getChildCount());

        // widgets

        Reports.Trace.Node widgets = trace.getRoot().getChild(0);
        assertTrue(widgets.getStartTime() > 0, "Field start time is greater than zero");
        assertTrue(widgets.getEndTime() > 0, "Field end time is greater than zero");
        assertEquals("Query", widgets.getParentType());
        assertEquals("[Widget!]", widgets.getType());
        assertEquals("widgets", widgets.getResponseName());
        assertEquals(2, widgets.getChildCount());

        Reports.Trace.Node secondItem = widgets.getChild(1);
        assertEquals(1, secondItem.getIndex());
        assertEquals(2, secondItem.getChildCount());

        Reports.Trace.Node foo = secondItem.getChild(0);
        assertTrue(foo.getStartTime() > 0, "Field start time is greater than zero");
        assertTrue(foo.getEndTime() > 0, "Field end time is greater than zero");
        assertEquals("Widget", foo.getParentType());
        assertEquals("String", foo.getType());
        assertEquals("foo", foo.getResponseName());
        assertEquals(0, foo.getErrorCount());

        Reports.Trace.Node bar = secondItem.getChild(1);
        assertTrue(bar.getStartTime() > 0, "Field start time is greater than zero");
        assertTrue(bar.getEndTime() > 0, "Field end time is greater than zero");
        assertEquals("Widget", bar.getParentType());
        assertEquals("String", bar.getType());
        assertEquals("baz", bar.getResponseName());
        // Widget.bar is aliased as baz
        assertEquals("bar", bar.getOriginalFieldName());
        assertEquals(1, bar.getErrorCount());

        Reports.Trace.Error error = bar.getError(0);
        assertEquals("whoops", error.getMessage());
        assertEquals(1, error.getLocationCount());
        assertEquals(18, error.getLocation(0).getColumn());
        assertEquals(1, error.getLocation(0).getLine());

        // listOfLists

        Reports.Trace.Node listOfLists = trace.getRoot().getChild(1);
        assertEquals(0, listOfLists.getChild(0).getIndex());
        assertEquals(2, listOfLists.getChild(0).getChildCount());
        assertEquals(1, listOfLists.getChild(1).getIndex());
        assertEquals(2, listOfLists.getChild(1).getChildCount());

        assertEquals(0, listOfLists.getChild(0).getChild(0).getIndex());
        assertEquals(1, listOfLists.getChild(0).getChild(0).getChildCount());
        assertEquals(0, listOfLists.getChild(0).getChild(1).getIndex());
        assertEquals(1, listOfLists.getChild(0).getChild(1).getChildCount());

        Reports.Trace.Node deeplyNestedFoo = listOfLists.getChild(0).getChild(0).getChild(0);
        assertTrue(deeplyNestedFoo.getStartTime() > 0, "Field start time is greater than zero");
        assertTrue(deeplyNestedFoo.getEndTime() > 0, "Field end time is greater than zero");
        assertEquals("Widget", deeplyNestedFoo.getParentType());
        assertEquals("String", deeplyNestedFoo.getType());
        assertEquals("foo", deeplyNestedFoo.getResponseName());
        assertEquals(0, deeplyNestedFoo.getErrorCount());

        // listOfScalars

        Reports.Trace.Node listOfScalars = trace.getRoot().getChild(2);
        assertTrue(listOfScalars.getStartTime() > 0, "Field start time is greater than zero");
        assertTrue(listOfScalars.getEndTime() > 0, "Field end time is greater than zero");
        assertEquals("Query", listOfScalars.getParentType());
        assertEquals("[String!]!", listOfScalars.getType());
        assertEquals("listOfScalars", listOfScalars.getResponseName());
    }
}
