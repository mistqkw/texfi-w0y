# How a version is released

Building and signing happen in GitHub Actions
([build.yml](../.github/workflows/build.yml)); locally only the debug
build is produced, to check the code.

## Signing secrets

Three secrets are set in the repository, and Actions assembles the release
key from them:

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the PKCS12 store `texfi-release.jks`, base64 |
| `ANDROID_STORE_PASSWORD` | store password |
| `ANDROID_KEY_PASSWORD` | key password (alias `texfi`) |

It is the same key as the other TexFi apps use. It is not in the
repository and must never be: a build without the secrets does not fail,
but it is signed with a debug key, and such an APK is not handed out.

## Releasing

```
git tag v0.0.1-beta-1 && git push origin v0.0.1-beta-1
```

From there the workflow builds and signs the APK, builds the Linux desktop
archive (`w0y-linux-x86_64.tar.gz`; if that step fails the APK is still
released, just without the archive), writes `SHA256SUMS.txt`, and creates the
GitHub Release named "v0.0.1 beta-1" with the description from
[RELEASE_NOTES.md](RELEASE_NOTES.md). Release names turn the tag's `-beta`
into a space; the release is marked pre-release.

Android `versionName` is the tag without the leading `v`
(`0.0.1-beta-1`). `versionCode` only ever goes up: Android refuses to install
a build with a lower code over an installed one, and the user would have to
uninstall the app together with its data.

## Things to know

**Write permission for the Actions token.** By default `GITHUB_TOKEN` is
read-only in the account, and the "Create release" step fails with
`403 Resource not accessible by integration` — even though building and
signing have already succeeded by that point. Fixed once in
*Settings → Actions → General → Workflow permissions* →
**Read and write permissions**.

**Pre-release and `/releases/latest`.** GitHub does not count a
pre-release as the "latest release", so `releases/latest/download/...` links
and the API endpoint `/releases/latest` do not see it. Link to a concrete
tag (`releases/download/v0.0.1-beta-1/...`) or list releases instead.
