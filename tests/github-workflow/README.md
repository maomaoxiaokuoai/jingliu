# GitHub workflow validation scope

This directory records local checks performed while packaging the repository-ready source archive.

- `build-apk.yml` parsed as YAML 1.2.
- Each embedded Bash `run:` block passed `bash -n`.
- Existing project source-structure checks passed.
- Kotlin/KTS PSI parsing found no syntax error nodes.

These checks do not execute GitHub Actions, resolve Android/Chaquopy dependencies, run AGP, or produce an APK. The first real CI result is the workflow run in the user's GitHub repository.
