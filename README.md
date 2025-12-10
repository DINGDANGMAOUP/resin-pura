# Resin Pura

<!-- Plugin description -->
An IntelliJ IDEA plugin that provides support for Resin application server integration.
<!-- Plugin description end -->

## Features

- Resin server configuration and management
- Deployment support for web applications
- JMX-based monitoring and control
- Support for multiple Resin versions (2.x, 3.x, 4.x)

## Installation

### From GitHub Releases

1. Download the latest plugin ZIP from [GitHub Releases](https://github.com/dingdangmaoup/resin-pura/releases)
2. In IntelliJ IDEA, go to `Settings/Preferences` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
3. Select the downloaded ZIP file
4. Restart the IDE

## Development

### Requirements

- JDK 21
- Gradle 8.13+

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
```

## Compatibility

- IntelliJ IDEA 2024.2+ (Build 242+)
- Supports IntelliJ IDEA Ultimate Edition

## License

MIT
