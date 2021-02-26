# Releasing this package


## One-time Sonatype setup
- Sign up for a Jira account at [https://issues.sonatype.org/](https://issues.sonatype.org/). Note that these are the credentials you'll use to publish to Maven Central.
- [Create a Jira task](https://issues.sonatype.org/secure/CreateIssue!default.jspa) on Sonatype's OSSRH board, asking for access to the group ID `com.apollographql.federation` so that you can publish this package. For an example of what this looks like, see [OSSRH-65026](https://issues.sonatype.org/browse/OSSRH-65026).
- Slack one of Martin Bonnin, Sachin Shinde, or Martijn Walraven, and ask them to comment on the created Jira task, to confirm that you should have access.
- Within a business day (hopefully), a Sonatype representative should reply and confirm that your permissions have been granted.

## One-time GPG setup
- Install `gpg` on your computer. You'll need this to sign files you upload to Maven Central, as Sonatype requires them to be signed.
- Ensure `gpg` is on your computer's path environment variable, as this is how Maven discovers it.
- Apollo uses a shared GPG key for Maven Central signing, located in 1Password. Copy-paste the private key (the block starting with `-----BEGIN PGP PRIVATE KEY BLOCK-----` and ending with `-----END PGP PRIVATE KEY BLOCK-----`) into a file called `private.key`.
- Run `gpg --import private.key`. A prompt will appear asking you for the passphrase; get this from 1Password as well.
- Delete the `private.key` file.

## For each release
- Start a branch `release-VERSION`
- Update RELEASE_NOTES.md
- Edit all instances of (next version)-SNAPSHOT in all pom.xml files to be the desired version
- Edit version in Gradle section of README.md
- Push branch, open PR, wait for CI to pass
- Run `SONATYPE_USERNAME=username SONATYPE_PASSWORD=password ./mvnw --settings settings.xml clean deploy`
- A prompt will appear asking you for the GPG passphrase; get this from 1Password
- Run `git tag vVERSION && git push origin vVERSION`
- Edit all instances of the version in all pom.xml files to be the next patch release plus `-SNAPSHOT`
- Push branch again
- Merge PR
