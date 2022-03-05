# Solar Patcher
![Discord](https://img.shields.io/discord/880500602910679112?color=404eed&logo=discord&logoColor=%23fff&style=for-the-badge)
![GitHub](https://img.shields.io/github/license/Solar-Tweaks/SolarPatcher?style=for-the-badge)
![Maintenance](https://img.shields.io/maintenance/yes/2022?style=for-the-badge)

Runtime agent to patch Lunar Client, easier and faster.
This is intended to be used by/with [Solar Tweaks](https://github.com/Solar-Tweaks/),
however you can use it by yourself using the `-javaagent` [flag](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html).

# User warning
This program modifies the code of Lunar Client on runtime.
This means that using this software is against Lunar Client TOS, using the launcher is however allowed.
We do not want to scare you away, but using this software is at YOUR OWN RISK.
NO warranty whatsoever is provided for this application, and support will not be granted for people that undergo consequences because of this software.  
USE AT YOUR OWN RISK.

# Features
Solar Patcher currently supports 26 distinctive modules, all very configurable.
A full list of these features can be found [here](Features.md)

# Downloading
You can download a prebuilt artifact from the [releases page](https://github.com/Solar-Tweaks/SolarPatcher/releases).

# Usage
In order to use Solar Patcher, you will need to use a third party software to launch Lunar Client.
You can use [Lunar Client Lite](https://github.com/Aetopia/LCLPy), or launch it through the command line using the [wrapper](https://github.com/Aetopia/Lunar-Client-Lite-Launcher/blob/main/wrapper.cmd) made by Aetopia and Lemons#2555.  

In the near future, a new launcher will be released that uses this patcher automatically.

## Configuration
Open a `config.json` file (anywhere you like) with a text editor. An example configuration is shipped with the repository, or can be generated with `gradlew defaultConfig`.
Edit the config as you wish. A part of the example configuration is shown below, with instructions on what to modify.
```json5
{
  "autoGG": {
    "from": "/achat gg", // Original string in the code, don't touch
    "to": "/ac Good game", // Replacement string, put whatever you want
    "isEnabled": false // Whether the module is enabled
  }
}
```
⚠️ **This is an example, most modules don't look like this.**  
Pro tip: use the `enableAll` flag to enjoy all modules without much configuration.

# Building from source
In order to build the project from source, you must have JDK 9 or higher installed.
If you don’t, we recommend downloading one from [OpenJDK](https://jdk.java.net/17/).
To build, clone this repository first using
```shell
$ git clone https://github.com/Solar-Tweaks/SolarPatcher.git
```
Once the repo is cloned, move to the directory.
```shell
$ cd SolarPatcher
```
Run the build command. An artifact will be generated in the `build/libs` directory.
```shell
$ gradlew build
```

# Useful tasks
These are tasks that can be run with the `gradlew` command. Useful for development.  
- `build`: Generate an artifact  
- `build -Pprod`: Same as `build`, but enabled the production configuration.  
- `defaultConfig`: Generates a config.example.json file with default values.  
- `updater`: Generates the `updater.json` file in the `build` directory, with data used for the updater in the launcher  
- `detekt`: Runs static analysis over the code with [detekt](https://github.com/detekt/detekt)  
- `lint`: Same as `detekt`  
- `clean`: Delete all compilation data/artifacts from disk.  

# Contributing
Since most work for patching efficiently has already been done,
you can fork and add modules to the Modules.kt and TextModules.kt files. PRs are welcome.