# Resin Pura Changelog

## [Unreleased]

## [4.1.4] - 2026-08-04

### Added
- Add regression coverage for JMX cleanup failures, secure JMX arguments, legacy Resin version detection, fallback strategy selection, configuration defaults, and port validation
- Add optional endpoint-scoped remote JMX credentials backed by IntelliJ Password Safe without serializing secrets into run configuration XML
- Add explicit CI tests against the IntelliJ 2024.2 compatibility floor and release artifact checksums
- Add contract tests for Resin archive MBeans, remote connector credential modes, IPv6 endpoints, Password Safe isolation, and configuration snapshot side effects

### Changed
- Detect Resin versions and capabilities by reading JAR metadata and static class-initializer assignments without loading Resin classes into the IDE process
- Require authenticated JMX credentials and bind local JMX/RMI endpoints to the loopback interface
- Use port 8080 for new Resin configurations, explicitly persisting it while preserving port 80 for legacy states that omitted the historical default
- Validate HTTP and JMX ports against the TCP port range
- Cache the detected version for each Resin installation and batch initial configuration deployment updates into one save
- Verify release artifacts with the full test and plugin-verifier gates before publishing them
- Deep-copy persisted server-model data for editable IntelliJ snapshots and validate charset values before applying configuration changes
- Reuse validated Resin installations while the selected server and `RESIN_HOME` remain stable, serialize first-use version detection, and keep Resin library classpath order deterministic
- Pass release notes to GitHub CLI through environment-backed files and reject ambiguous build artifact sets

### Fixed
- Report deployment and undeployment cleanup failures instead of treating them as successful operations
- Select compatible strategies for `2.x`, `3.x`, and unknown fallback versions without numeric parsing failures
- Create missing Resin 3 `<javac args>` configuration before enabling debugger information
- Match hierarchical Resin version wildcards such as `3.1.x` when validating paths containing spaces
- Prevent `resin.properties` from overriding local JMX authentication, credential files, port, or loopback bindings
- Prevent Resin command-line parameters from weakening JMX security in watchdog child JVMs
- Re-assert local JMX security after common VM options and run extensions are appended
- Preserve unrelated Resin arguments when removing a dangling `-jmx-port` override
- Resolve `${__DIR__}` in Resin import/properties path attributes against each source configuration file, including copied nested imports
- Handle case-insensitive ROOT and special-character deployment names as exact JMX ObjectNames, refresh Resin's archive index before commands, and use extension-free archive keys for start/undeploy operations
- Prevent IntelliJ configuration snapshots from writing Password Safe or shared transport-target state before the real configuration is applied
- Replace disabled or conflicting Resin javac debug options with one authoritative `-g` option
- Fail remote transfers cleanly when IntelliJ cannot resolve the source artifact as a virtual file
- Point Dependabot updates at the repository's active `main` branch

## [4.1.3] - 2026-08-04

### Added
- Add regression coverage for deployment provider compatibility, serialization defaults and round trips, and Resin import scanning

### Changed
- Move deployment provider ownership to `AppServerIntegration` for both local and remote models, retaining the deprecated `ServerModel` hook only as a nullable compatibility bridge
- Replace deprecated XML serialization filters with the public configuration-store serializer
- Replace deprecated browse-folder overloads with listeners available across IntelliJ IDEA 2024.2–2026.2
- Replace deprecated JDOM XPath queries with ordered descendant traversal while preserving Resin import semantics

## [4.1.2] - 2026-07-20

### Changed
- Update the IntelliJ Platform target to `2026.2`
- Expand the compatible build range to `262.*`
- Upgrade the Kotlin Gradle Plugin from `2.3.10` to `2.3.20` for supported Gradle 9.2 builds

### Fixed
- Restore builds against IntelliJ IDEA 2026.2 by upgrading the IntelliJ Platform Gradle Plugin from `2.11.0` to `2.18.1`
- Serialize production and test bytecode instrumentation to prevent clean parallel CI builds from racing

## [4.1.0] - 2026-04-08

### Changed
- Modernize the Resin run configuration and deployment editors with the Kotlin UI DSL
- Improve remote host and target validation
- Replace obsolete utility code with modern Kotlin APIs and stronger null-safety checks
- Refresh the plugin icon

## [3.0.0] - 2025-12-10

### Added
- Initial release of Resin Pura plugin
- Support for Resin application server integration
- Deployment support for web applications
- JMX-based monitoring and control
- Support for multiple Resin versions (2.x, 3.x, 4.x)

[Unreleased]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.4...HEAD
[4.1.4]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.3...v4.1.4
[4.1.3]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.2...v4.1.3
[4.1.2]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.0...v4.1.2
[4.1.0]: https://github.com/dingdangmaoup/resin-pura/releases/tag/v4.1.0
[3.0.0]: https://github.com/dingdangmaoup/resin-pura/releases/tag/v3.0.0
