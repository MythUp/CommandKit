# CommandKit

CommandKit is a client-only Fabric library mod for Minecraft 26.2.
It provides a reusable CommandKit API for other client mods.

## Features

- Command nodes with aliases and nested subcommands.
- Player, literal, player-or-literal, repeatable, and rest-of-message arguments.
- Dynamic player suggestions from the current client connection.
- Server/context predicates for enabling definitions only where appropriate.
- Vanilla and server suggestions remain available when no local definition matches.

## Use from another mod

Maven coordinates:

```text
com.hypixelcommands:commandkit:1.0.0+26.2
```

```groovy
repositories {
    mavenLocal()
    // Add your release Maven repository here.
}

dependencies {
    implementation "com.hypixelcommands:commandkit:1.0.0+26.2"
}
```

Register definitions from the consumer mod:

```java
CommandNode language = new CommandNode("lang", "language")
        .then(new CommandNode("english", "french", "german"));

language.when(context ->
        context.serverAddress() != null
                && context.serverAddress().endsWith("example.net"));

CommandCompletion.register(language);
```

Install the published `commandkit` jar as a separate Fabric mod.
Consumer mods must not add another `CommandSuggestions` mixin.

## Build

```text
gradlew build
gradlew publishMavenJavaPublicationToMavenLocal
```

The project requires Java 25, Fabric Loader 0.19.3, and Minecraft 26.2.

## License

See [LICENSE](./LICENSE).
