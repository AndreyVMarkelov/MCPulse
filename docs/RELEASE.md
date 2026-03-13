# Release and Publishing

Release is tag-driven via GitHub Actions.

Workflow:

- `.github/workflows/release.yml`

## Trigger

Push a tag that matches `v*`, for example:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Versioning

- CI derives release version from tag:
- `v1.0.0` -> `1.0.0`
- passed to Gradle as `-PreleaseVersion=<derived>`

Local fallback version remains snapshot style (`1.0.0-SNAPSHOT`) when no release property/tag is provided.

## Publish Targets

1. GitHub Packages
2. Maven Central
3. GitHub Release (artifacts)
4. Plugins Manager descriptor (`plugins-repo.json`) as a GitHub Release asset

## Required GitHub Secrets (Maven Central)

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `MAVEN_SIGNING_PRIVATE_KEY`
- `MAVEN_SIGNING_KEY_ID` (optional)
- `MAVEN_SIGNING_PASSPHRASE`

## Notes

- publish uses signed artifacts
- release fails early if tag format is invalid
- release also uploads:
- `jmeter-mcp-sampler-latest.jar` (stable latest asset name)
- `plugins-repo.json` (Plugins Manager descriptor pointing to latest jar asset)
