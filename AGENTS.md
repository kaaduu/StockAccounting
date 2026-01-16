# AI Agent Instructions

This project is actively maintained with the help of AI agents. To ensure consistency and high-quality documentation, follow these rules during every session:

1.  **Update CHANGES.md**: Every time you complete a significant task or implement a new feature, update `CHANGES.md` with a concise summary of the changes. This is the source of truth for the project's evolution.
2.  **Language Requirements**: All changes to `README.md` and `CHANGES.md` must be written only in Czech language. Do not add or modify English content in these files.
3.  **Modernization Standards**: Adhere to Java 17+ coding standards. Avoid deprecated APIs and prefer modern alternatives (e.g., `java.time` for date/time logic, generics for collections).
4.  **Documentation First**: Ensure all new files or major architectural changes are documented in the relevant `.md` files. Maintain the `walkthrough.md` if significant new workflows are added.
5.  **Verify Build Integrity**: Always run `./build.sh` before concluding a task to ensure the project remains in a compilable state.
6.  **Git Best Practices**: Follow the project's git commit workflow (using temporary files for commit messages) and always verify the current branch before committing.
7.  **Default Git Remote**: Use `origin` as the default git remote for all operations. When performing pull activities, also sync with the `gitea` repository. The `create-release-tag.sh` script should push to `origin` by default but may also sync with `gitea`.
8.  **Versioning Scheme**: Use date-based versioning format `vYYYY.MM.DD` or `vYYYY.MM.DD-suffix` as defined in `create-release-tag.sh`. Avoid semantic versioning (e.g., v1.0.0, v2.1.0) to maintain consistency with the established release workflow.
