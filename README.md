# josm-atlas

[![Build Status](https://travis-ci.org/osmlab/josm-atlas.svg?branch=master)](https://travis-ci.org/osmlab/josm-atlas)

This is a plugin for JOSM that allows .atlas files to be opened and visualized.

You must be on JOSM v12712 or later for the latest build to work.

## Installation

### Build

Clone the project and edit the build.gradle to change the josm version to your JOSM's version:

```
project.ext.josmVersion = '12712'
```

Then run:

```
gradle downloadJosm
```

and

```
gradle installPlugin
```

### Launch

Open JOSM and navigate to your Plugin Preferences.

    Edit > Preferences > Plugins

Type `josm-atlas` into the search box and select the plugin.

## Contributing

To contribute to the project, see the [contributing guidelines](https://github.com/osmlab/atlas/blob/dev/CONTRIBUTING.md)