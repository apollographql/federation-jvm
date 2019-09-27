# Releasing this package

- Sign up for an account at bintray.com
- Ask an admin to add you to the apollographql organization (eg pcarrier or glasser)
- Create an API key in your Bintray profile
- Update RELEASE_NOTES.md
- Edit all instances of (next version)-SNAPSHOT in all pom.xml files to be the desired version
- Run `BINTRAY_USER=username BINTRAY_API_KEY=apikey ./mvnw --settings settings.xml clean deploy`
- `git tag vVERSION && git push origin vVERSION`
- Edit all instances of the version in all pom.xml files to be the next patch release plus `-SNAPSHOT`
