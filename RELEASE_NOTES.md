# Release notes

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
