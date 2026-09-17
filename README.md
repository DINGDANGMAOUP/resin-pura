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

### Deployment modes

| Mode | Target and behavior |
| --- | --- |
| Configuration, local Resin 2/3/4 | Edits a generated copy of the selected XML, scoped to the selected host and context. Deploying after undeployment restores the entry. Requires writable configuration mode. |
| JMX, local Resin 3/4 | Uses the `Host=default,name=webapps` archive deployer and its reported archive/expansion directories. The context is derived from the archive name (`ROOT.war` maps to `/`). Custom hosts or mismatched explicit contexts are rejected. |
| JMX, remote Resin 3/4 | Uses the same default archive deployer. Configure remote transfer to its deployment directory; arbitrary deployer names and hosts are not supported. |

A configuration write or accepted JMX command does not establish that an application is running. The plugin reports deployment success only after observing an active application through JMX; unavailable or transitional observations remain unknown. Automatic observation is bounded to two minutes. Resin 2 configuration deployments remain unknown because this integration has no runtime status observer for them. Resin must reload the generated XML for running configuration changes to take effect.

Generated configuration files and copied imports belong to one run and are removed on preparation failure, process creation failure, or process exit. The original source configuration is preserved. Local JMX deployment rejects source/destination overlap; replacement is not transactional, so a failed replacement can require redeployment.

## Installation

### From GitHub Releases

1. Download the latest plugin ZIP from [GitHub Releases](https://github.com/dingdangmaoup/resin-pura/releases)
2. In IntelliJ IDEA, go to `Settings/Preferences` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
3. Select the downloaded ZIP file
4. Restart the IDE

## Development

### Requirements

- JDK 21
- The checked-in Gradle wrapper (currently 9.6.1)

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

To reuse locally installed IDEs instead of downloading them:

```bash
./gradlew test -PplatformVersion=2024.2 -PplatformLocalPath=/path/to/IDE/Contents
./gradlew verifyPlugin -PpluginVerifierLocalIde=/path/to/IDE/Contents
```

The default verifier still checks the recommended IDE matrix; the local override checks only the supplied IDE.

## Compatibility

- IntelliJ IDEA 2024.2–2026.2 (Build 242–262.*)
- IntelliJ IDEA Ultimate Edition

The JDK 21 requirement above is for building the plugin. Select the Resin process JDK according to the particular Resin release and application; supporting a Resin configuration format does not guarantee that an old Resin release runs on a modern JDK. The plugin adds debug symbols without injecting a Java 5 source level, and preserves explicitly configured language levels.

## Upstream Source
JetBrains obsolete plugin repository (original Resin plugin):
https://github.com/JetBrains/intellij-obsolete-plugins/tree/master/resin
