# lunar-util
This submodule contains utility classes for working with Lunar Client.

### LunarMapper
The LunarMapper class is used to remap mainly Minecraft's classes, but also Optifine, according
to the mappings provided in the lunar-prod-optifine.jar file. These mappings change classnames,
and also patch in mixins. Useful when reversing Lunar Client.

#### Usage
*The Minecraft Jar path is usually located in `.minecraft/versions/<version>/<version>.jar`*
```shell
./gradlew runMapper --args "<lunar-prod-optifine path> <minecraft jar path> [<output file>]"
```

### ModIdExtractor
The ModIdExtractor class is used to find all identifiers of "FeatureDetails", but I simply called
them "mod IDs". The program yields all mod IDs it can find, and it also supports streaming.

#### Usage
```shell
./gradlew runExtractor --args "<lunar-prod-optifine path>"
```

*Note: This way, you cannot stream the output, since your log will be filled with gradle logs.
You can use this command to get the desired result:*
```shell
java -cp lunar-util-vX.X.jar com.grappenmaker.solarpatcher.util.ModIdExtractor /path/to/lunar-prod-optifine.jar > output.txt
```

### LunarLauncher
The LunarLauncher class is used to launch a remapped lunar client jarfile (produced by the `LunarMapper`)
which in general is much faster than the normal launcher, and allows for the usage of tools like
a debugger, profiler or alike.  

*Note: currently, if you use the default resource pack, because
lunar client forces connected glass textures, which get patched in by a custom ClassLoader
the textures won't show up. You might want to apply a pack that provides glass textures*

#### Usage
*Note: the version is the name of the directory in the .lunarclient/offline directory*  
*Note: nativesPath is usually located at the .lunarclient/offline/<version>/natives directory,
the location of .lunarclient depends on your operating system, but it is always located in your home directory*  
*Note: in the passthrough arguments, you at least need to have a --accessToken and a --version argument*
*If you want cosmetics, provide a --texturesDir*

```shell
./gradlew runLauncher -Pnatives=<nativesPath> --args "<remapped jar path> <version> [<pass through arguments>]"
```