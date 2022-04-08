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