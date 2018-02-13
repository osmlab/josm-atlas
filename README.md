# josm-atlas

[![Build Status](https://travis-ci.org/osmlab/josm-atlas.svg?branch=master)](https://travis-ci.org/osmlab/josm-atlas)

This is a plugin for JOSM that allows .atlas files to be opened and visualized.

You must be on JOSM v12712 or later for the latest build to work.

## Installation

Each time you run
```bash
./gradlew installPlugin
```
the plugin is built if needed. The resulting \*.jar file is activated for your already installed JOSM instance.
This task then fires up that JOSM instance and you can use the plugin right away.

After that you can start JOSM however you want, the plugin will still be installed. You'd only have to repeat this, when you want to later update to a newer version of the plugin.

## Development

### Launch a clean JOSM instance

When you run
```bash
./gradlew runJosm
```
a clean JOSM instance is fired up containing only the `josm-atlas` plugin.

You do **not** have to have JOSM already installed for this: Gradle will download a JOSM executable and run that.

A new `JOSM_HOME` directory is created (separate from any existing ones). It persists between calls to this task, but can be cleared using the `cleanJosm` task.

### Build

Run
```bash
./gradlew build
```
to start a full build.

## Contributing

To contribute to the project, see the [contributing guidelines](https://github.com/osmlab/atlas/blob/dev/CONTRIBUTING.md)
