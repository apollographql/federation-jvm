# Release notes

## v0.6.1

*Bugfixes:*
- Fix bug where `Federation.transform()` would remove all query type fields from a schema with query type extensions but an empty query type.

## v0.6.0

This is an upgrade release for graphql-java v16. This release is incompatible with earlier graphql-java versions due to backwards-incompatible changes in their API.

Note that if your schema has an empty query type/no query type and you pass a `GraphQLSchema` object to this library, your `GraphQLSchema` will no longer build in graphql-java v16 due to a new validation. To work around this, you can add a dummy field to your `GraphQLSchema`'s query type, and then pass `queryTypeShouldBeEmpty` as `true` to `Federation.transform()`. The output schema won't contain the dummy field, nor will it be visible to the gateway. If you instead pass a different representation of a schema to `Federation.transform()` (e.g. a string), you don't have to do anything; this library will handle the issue internally.

*Upgrades:*
- graphql-java to `16.1`.

## v0.5.0

This is an upgrade release for graphql-java v15. This release is incompatible with earlier graphql-java versions due to backwards-incompatible changes in their `SchemaPrinter`.

*Upgrades:*
- graphql-java to `15.0`.

## v0.4.3

*Bugfixes:*
- Fix crash in federated tracing support when an error with a null location is thrown.

## v0.4.2

*Bugfixes:*
- Backport bugfix in graphql-java v15's `SchemaPrinter` regarding escaping in single-quoted descriptions to `FederationSdlPrinter`.

## v0.4.1

*Bugfixes:*
- Update `@extends` definition to include interfaces, as per GraphQL spec

## v0.4.0

This is an upgrade release for graphql-java v14. This release is incompatible with earlier graphql-java versions due to breaking changes in their API.

*Upgrades:*
- graphql-java to `14.0`.

## v0.3.4

*Bugfixes:*
- Remove bad description text in `_FieldSet.type`.
- Remove federation directive definitions and type definitions from `query { _service { sdl } }`.

*Enhancements:*
- Enforce that directives used in a `TypeDefinitionRegistry` have a definition and are valid according to that definition.
- Automatically add federation directive definitions and type definitions to `TypeDefinitionRegistry` if it doesn't already have them.

## v0.3.3

This release requires graphql-java v13, due to the backport below.

*Bugfixes:*
- Fix thread-safety bug in federated tracing support.
- Backport bugfix in graphql-java's `SchemaPrinter` to graphql-java v13.

## v0.3.2

*Bugfixes:*
- Fix crash in federated tracing support (new in v0.3.1) when an error with a null message is thrown.

## v0.3.1

*Enhancements:*
- Federated tracing support.

## v0.3.0

Accidental release with no changes.

## v0.2.0

*Bugfixes:*
- When an interface is `@key`ed, its concrete types are added to `_Entity`.

*Enhancements:*
- Range of signatures for `Federation.transform`
- Declaring the query root type in the original schema is optional.

*Upgrades:*
- spring-boot to `2.1.6.RELEASE`.

## v0.1.0

First version tested against the federated Apollo gateway.
