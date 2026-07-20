# Resin Pura Changelog

## [Unreleased]

## [4.1.2] - 2026-07-20

### Changed
- Update the IntelliJ Platform target to `2026.2`
- Expand the compatible build range to `262.*`
- Upgrade the Kotlin Gradle Plugin from `2.3.10` to `2.3.20` for supported Gradle 9.2 builds

### Fixed
- Restore builds against IntelliJ IDEA 2026.2 by upgrading the IntelliJ Platform Gradle Plugin from `2.11.0` to `2.18.1`

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

[Unreleased]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.2...HEAD
[4.1.2]: https://github.com/dingdangmaoup/resin-pura/compare/v4.1.0...v4.1.2
[4.1.0]: https://github.com/dingdangmaoup/resin-pura/releases/tag/v4.1.0
[3.0.0]: https://github.com/dingdangmaoup/resin-pura/releases/tag/v3.0.0
