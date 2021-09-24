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
1. Start a branch `release-VERSION`.
1. Update RELEASE_NOTES.md.
1. Run `tag.main.kts $version` to update all versions in pom files/README and create a tag.
1. Push branch, open PR, wait for CI to pass.
1. Ensure you are using the latest Zulu build of OpenJDK 8.
1. Checkout the tag created in step 3: `get checkout v$version`
1. Run `SONATYPE_USERNAME=username SONATYPE_PASSWORD=password ./mvnw --settings settings-release.xml clean deploy`
1. A prompt will appear asking you for the GPG passphrase; get this from 1Password.
1. If everything went well, merge the PR
1. And push the tag: `git push origin vVERSION`
