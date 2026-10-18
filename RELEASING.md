# Releasing RocketLauncher

Releases are built, signed and published by GitHub Actions
([`.github/workflows/release.yml`](.github/workflows/release.yml)) when a version tag is pushed.

## Making a release

1. In `app/build.gradle`, raise `versionCode` by one and set `versionName`, e.g. `1.1.0`.
   Android only installs an update whose `versionCode` is higher than the installed one.
2. Merge that change to `master`.
3. Tag the merge commit and push the tag:

   ```
   git checkout master && git pull
   git tag v1.1.0
   git push origin v1.1.0
   ```

   The tag must be `v` followed by `versionName`, or the workflow stops. A tag with a suffix,
   like `v1.1.0-beta.1`, is published as a pre-release.

The workflow builds the APK, checks it is signed with the release key and publishes
`RocketLauncher-v1.1.0.apk` with notes generated from the merged pull requests.

## The signing key

Every release must be signed with the same key, or it can't update copies already installed.
The key was made on the maintainer's computer:

- `~/.android-keys/rocketlauncher-release.jks`: the key (alias `rocketlauncher`)
- `~/.android-keys/rocketlauncher-release.properties`: its location and passwords

Neither belongs in this repository; `.gitignore` excludes them. Keep a backup of both somewhere
private, like a password manager. Certificate SHA-256:
`44:2F:88:4F:5A:5D:01:70:48:27:B2:27:FD:26:A3:36:FC:E4:0B:46:4E:5D:9A:6E:D5:B1:BB:E0:E9:84:51:D0`

GitHub Actions reads the key from these repository secrets
(Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | The `.jks` file, base64 encoded: `base64 -w0 ~/.android-keys/rocketlauncher-release.jks` |
| `RELEASE_STORE_PASSWORD` | `RELEASE_STORE_PASSWORD` from the `.properties` file |
| `RELEASE_KEY_ALIAS` | `rocketlauncher` |
| `RELEASE_KEY_PASSWORD` | `RELEASE_KEY_PASSWORD` from the `.properties` file |

On the maintainer's computer, `./gradlew assembleRelease` signs with the key from the
`.properties` file directly, which is handy for testing a release build before tagging.
