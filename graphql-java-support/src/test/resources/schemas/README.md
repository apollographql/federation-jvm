This directory contains various federation schemas that verify the transformations.
When creating a new valid (happy path) test case please create a new directory that
contains

* `schema.graphql` - schema file that will be transformed
* `schema_full.graphql` - transformed complete GraphQL schema (includes all directive definitions)
* `schema_federated.graphql` - transformed GraphQL schema returned from `_service { sdl }` query

When creating test cases that verify validations on broken schemas please name your test schema
with `invalid` prefix.
