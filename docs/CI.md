# CI

This project uses GitHub Actions.

Workflows:

- `.github/workflows/ci.yml`
- `.github/workflows/release.yml`

## `ci.yml`

Purpose:

- run quality checks and tests on pushes/PRs
- run docker smoke scenario
- upload reports/artifacts

Main jobs:

1. `quality-and-tests`
- Java matrix (`11`, `17`)
- runs `./gradlew check --no-daemon`
- uploads Checkstyle and test reports

2. `docker-smoke`
- validates compose config
- runs `mock-echo` docker profile
- uploads docker logs/results

## Artifacts Uploaded

- Gradle quality/test reports
- Docker smoke scenario outputs

## Local Equivalent

```bash
./gradlew check --no-daemon
docker compose --profile mock-echo up --build --abort-on-container-exit
```
