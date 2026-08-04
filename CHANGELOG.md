# Resin Pura Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.3...HEAD
[4.1.3]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.2...v4.1.3
[4.1.2]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.0...v4.1.2
[4.1.0]: https://github.com/dingdangmaoup/resin-pura/releases/tag/v4.1.0
[3.0.0]: https://github.com/dingdangmaoup/resin-pura/releases/tag/v3.0.0
