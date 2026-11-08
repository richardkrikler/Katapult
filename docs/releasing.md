# Releasing

Pushing a `v*` tag that points at a commit on `main` runs `.github/workflows/build.yml`:
the CI checks, a signed `assembleRelease`, and a GitHub Release with the APK that Obtainium follows.

## Steps

1. Run the same checks as CI:
   ```sh
   ./gradlew testDebugUnitTest assembleDebug detekt ktlintCheck
   ```
2. In `CHANGELOG.md`, rename `## [Unreleased]` to `## [1.6-rk.N]`. That section becomes the release notes.
3. Commit, then push `main` and the tag:
   ```sh
   git push origin main
   git tag v1.6-rk.N && git push origin v1.6-rk.N
   ```

## How versions work

- The tag is the version: `v1.6-rk.N` builds `versionName` `1.6-rk.N`. The part before `-rk` is the upstream release this is based on.
- `versionCode` is `1000 + run number`, so it is always above upstream's.
- The values in `app/build.gradle.kts` only apply to local builds, so upstream version bumps never conflict.
- Don't mark these tags as prereleases: `/releases/latest` and Obtainium skip prereleases.
- The in-app update checker follows `GITHUB_REPOSITORY`, so it reads this fork's releases.

## One-time setup: signing

1. Generate a keystore and keep it **outside** the repo, with a backup: without it, users can't update.
   ```sh
   keytool -genkeypair -v -keystore katapult-rk.jks -keyalg RSA -keysize 4096 -validity 10000 -alias katapult
   ```
2. Under Settings → Secrets and variables → Actions, add these repository secrets:
   - `KEYSTORE_BASE64`: the output of `base64 -i katapult-rk.jks | pbcopy`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS` (`katapult`)
   - `KEY_PASSWORD`

The build fails if `KEYSTORE_BASE64` is missing, because an unsigned APK can't be installed.

Optional: protect `main` with a branch ruleset and restrict who can create `v*` tags.
