# Resin Pura

<!-- Plugin description -->
An IntelliJ IDEA plugin that provides support for Resin application server integration.
This project is a maintained continuation of JetBrains' obsolete Resin plugin, updated for modern IntelliJ Platform versions.
<!-- Plugin description end -->

## Background

This project was originally based on JetBrains open-source Resin plugin:
https://github.com/JetBrains/intellij-obsolete-plugins/tree/master/resin

Because the original plugin is no longer maintained and became incompatible with newer IntelliJ IDEA versions, this repository upgrades and migrates it to keep Resin support working on current IDE releases.

## Features

- Resin server configuration and management
- Deployment support for web applications
- JMX-based monitoring and control
- Optional remote JMX credentials stored per endpoint in IntelliJ Password Safe
- Loopback-only authenticated JMX defaults for local Resin processes
- Support for multiple Resin versions (2.x, 3.x, 4.x)

Remote credentials are protected at rest by IntelliJ Password Safe. JMX/RMI transport encryption remains server-controlled, so use TLS or a trusted tunnel across untrusted networks.

## Installation

### From GitHub Releases

1. Download the latest plugin ZIP from [GitHub Releases](https://github.com/dingdangmaoup/resin-pura/releases)
2. In IntelliJ IDEA, go to `Settings/Preferences` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
3. Select the downloaded ZIP file
4. Restart the IDE

## Development

### Requirements

- JDK 21
- Gradle 9.2+

### Building

```bash
./gradlew buildPlugin
```

The plugin ZIP will be created in `build/distributions/`.

### Running

```bash
./gradlew runIde
```

### Testing

```bash
./gradlew check
./gradlew test -PplatformVersion=2024.2
./gradlew verifyPlugin
```

## Compatibility

- IntelliJ IDEA 2024.2–2026.2 (Build 242–262.*)
- IntelliJ IDEA Ultimate Edition

## Upstream Source
JetBrains obsolete plugin repository (original Resin plugin):
https://github.com/JetBrains/intellij-obsolete-plugins/tree/master/resin
