# Solar Patcher 🛠️
![Discord](https://img.shields.io/discord/880500602910679112?color=404eed&logo=discord&logoColor=%23fff&style=for-the-badge)
![GitHub](https://img.shields.io/github/license/Solar-Tweaks/SolarPatcher?style=for-the-badge)
![Maintenance](https://img.shields.io/maintenance/yes/2022?style=for-the-badge)

Runtime agent to patch Lunar Client, easier and faster.
This is intended to be used by/with [Solar Tweaks](https://github.com/Solar-Tweaks/Solar-Tweaks),
however you can use it by yourself using the `-javaagent` [flag](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html).

# Downloading ⬇️
You can download a prebuilt artifact from the [releases page](https://github.com/Solar-Tweaks/SolarPatcher/releases).

# Usage ⚒️
In order to use Solar Patcher, you will need to use a third party software to launch Lunar Client.
You can use [Lunar Client Lite](https://github.com/Aetopia/LCLPy), or launch it through the command line using the [wrapper](https://github.com/Aetopia/Lunar-Client-Lite-Launcher/blob/main/wrapper.cmd) made by Aetopia and Lemons#2555. In the near future, a new launcher will be released that uses this patcher automatically.

## Configuration
Open a `config.json` file (anywhere you like) with a text editor. An example configuration is shipped with the repository, or can be generated with `gradlew saveDefaultConfig`.
Edit the config as you wish. A part of the example configuration is shown below, with instructions on what to modify.
```json5
{
  "autoGG": {
    "from": "/achat gg", // Original string in the code, don't touch
    "to": "/ac Good game", // Replacement string, put whetever you want
    "method": { // You will need to change those fields to match the current Lunar Client mappings
      "name": "llIlIIIllIlllllIllIIIIIlI", 
      "descriptor": "(Llunar/aH/llIllIIllIlIlIIIIlIlIllll;)V"
    },
    "className": "lunar/bv/llIIIllIIllIIIllIIlIllIIl",
    "isEnabled": false // Whether or not the module is enabled
  }
}
```
⚠️ **This is an example, most modules don't look like this.**  
Pro tip: use the `enableAll` flag to enjoy all modules without much configuration.

## Run the game using Lunar Client Lite
Once you have configured the patcher, you can launch the game using the `Lunar Client Lite` launcher (or other third-party software to launch the game with arguments).
For Lunar Client Lite, go to the `Settings` tab and click `Edit` under `Edit LCLPY's Settings:`.
This will open your default text editor, with a file that looks like this:
```
[...]

[Java]
arguments = -Xms3G -Xmx3G -Xmn1G -XX:+UnlockExperimentalVMOptions [...] -javaagent:/path/to/solar-patcher.jar=/path/to/config.json

[...]
```
Just add the `-javaagent:/path/to/solar-patcher.jar=/path/to/config.json` at the end of this line like on the text above.
Replace the paths with the locations of your Solar Patcher artifact and the configuration file, respectively.

# Building from source 🏗️
In order to build the project from source, you must have JDK 8 or higher installed.
If you don’t, we recommend downloading one from [OpenJDK](https://jdk.java.net/17/).
To build, clone this repository first using
```bash
$ git clone https://github.com/Solar-Tweaks/SolarPatcher.git
```
Once the repo is cloned, move to the directory.
```bash
$ cd SolarPatcher
```
Run the build command. An artifact will be generated in the `build/libs` directory.
```bash
$ gradlew build
```

# Contributing
Since most work for patching efficiently has already been done, you can fork and add modules to the Modules.kt file. PRs are welcome.