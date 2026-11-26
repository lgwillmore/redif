# Publishing

The library modules (`redif-core`, `redif-in-memory`, `redif-lettuce`, `redif-compatibility`) apply the `buildsrc.convention.publish` convention, which
publishes them to Maven Central under `codes.laurence.redif` with the
[vanniktech Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/): jar, sources, a
javadoc jar holding the Dokka API reference, Gradle module metadata and POM, all signed.

## Versions

The version comes from git. A commit carrying a release tag (`X.Y.Z`, no `v` prefix) builds that version; any other
commit builds a `-SNAPSHOT` such as `0.2.0-3-gabc1234-SNAPSHOT`, which can never become a Maven Central release. Only
the Release workflow creates release tags.

Before Maven Central, Redif was published to a private AWS CodeArtifact repository as `io.plurex:redif` with
`v`-prefixed tags up to `v0.1.1`. Each of those commits also carries the plain tag (`0.0.1` to `0.1.1`) so the bump
counts from `0.1.1` and no Central version reuses a CodeArtifact one. The first Central release is the first under
`codes.laurence.redif`: it renames the packages and splits the implementations into `redif-in-memory` and `redif-lettuce`, so
`minor` gives `0.2.0`.

## Releasing

Go to Actions → Release → Run workflow on `main`, and choose `patch`, `minor` or `major`. The workflow:

1. runs the Check workflow (ktfmt and the full test suite, with Redis from `docker-compose.yml`);
2. tags the commit locally with the next version, bumped from the highest `X.Y.Z` tag;
3. runs `./gradlew publishAndReleaseToMavenCentral`, which uploads both modules as one deployment, releases it, and
   waits for Central to validate it;
4. pushes the tag and creates a GitHub release with generated notes.

A failed check or publish pushes no tag, so the workflow can simply be run again. A Maven Central release is
permanent: a version can never be replaced or deleted. It is usually downloadable within 30 minutes and searchable
on central.sonatype.com a little later. If publishing succeeded but the tag push failed, push the tag by hand
(`git tag -a X.Y.Z <commit> -m "Release X.Y.Z" && git push origin X.Y.Z`); re-running would fail because Central
rejects an existing version.

## One-time setup

1. Sign in to the [Central Portal](https://central.sonatype.com/). The `codes.laurence` namespace is already
   verified for Akashic and Warden; `codes.laurence.redif` falls under it, so no new namespace is needed.
2. Generate a user token, or reuse the Akashic and Warden token: Account → Generate User Token. Tokens are per
   account, not per project.
3. Reuse the Akashic and Warden signing key, whose public half is already on the key servers, or create one:

   ```shell
   gpg --quick-gen-key "Your Name <you@example.com>" ed25519 sign never
   gpg --list-secret-keys --keyid-format long          # the key ID follows "ed25519/"
   gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
   gpg --keyserver keys.openpgp.org --send-keys KEY_ID
   gpg --armor --export-secret-keys KEY_ID             # the value of SONATYPE_SIGNING_KEY
   ```

4. Add the repository secrets under Settings → Secrets and variables → Actions:

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_PORTAL_USERNAME` | user token username |
   | `MAVEN_CENTRAL_PORTAL_PASSWORD` | user token password |
   | `SONATYPE_SIGNING_KEY` | ASCII-armored private key, including the `BEGIN`/`END` lines |
   | `SONATYPE_SIGNING_PASSWORD` | the key's passphrase |

5. Remove the CodeArtifact repository's `io.plurex:redif` packages once nothing resolves them, and the AWS secrets
   the old workflows used.

## Publishing from a machine

`./gradlew publishToMavenLocal` installs the current snapshot in `~/.m2` for trying it in another project; snapshots
need no signing key.

To rehearse a release without making it public, tag a commit locally (`git tag 0.2.0`, never pushed), provide the
credentials, and upload a deployment that waits for a manual decision:

```shell
export ORG_GRADLE_PROJECT_mavenCentralUsername=...
export ORG_GRADLE_PROJECT_mavenCentralPassword=...
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys KEY_ID)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=...
./gradlew publishToMavenCentral
```

Inspect the deployment under Publishing → Deployments in the Central Portal, then drop it there and delete the local
tag (`git tag -d 0.2.0`). Pressing Publish there instead makes it a permanent release that has no pushed tag.
