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
git tag v0.0.1-beta && git push origin v0.0.1-beta
```

From there the workflow builds, signs and creates the GitHub Release with
the description from [RELEASE_NOTES.md](RELEASE_NOTES.md).

## Two rakes already stepped on

**Write permission for the Actions token.** By default `GITHUB_TOKEN` is
read-only in the account, and the "Create release" step fails with
`403 Resource not accessible by integration` — even though building and
signing have already succeeded by that point. Fixed once in
*Settings → Actions → General → Workflow permissions* →
**Read and write permissions**. Because of this the first v0.0.1-beta
release had to be created by hand: the APK was taken from a manual run's
artifact and published with `gh release create`.

**The "pre-release" flag.** We do not set it, even though the versions are
beta. The texfi-hub site reads the release through `/releases/latest`, and
that endpoint skips pre-releases: with the flag set, the w0y card would be
left without a version and without a download button. For the same reason
f0kus, files and m0ney publish their beta releases without it.
