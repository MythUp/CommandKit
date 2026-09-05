package com.hypixelcommands.commandkit;

import java.util.List;

/**
 * A positional argument in a {@link CommandNode}.
 */
public record CommandArgument(
        String name,
        ArgumentType type,
        List<String> literals,
        boolean repeatable
) {
    public CommandArgument {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Argument name cannot be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Argument type cannot be null");
        }
        literals = literals == null ? List.of() : List.copyOf(literals);
        if (repeatable && type == ArgumentType.REST_MESSAGE) {
            throw new IllegalArgumentException("A rest message cannot be repeatable");
        }
    }

    public static CommandArgument string(String name) {
        return new CommandArgument(name, ArgumentType.STRING, List.of(), false);
    }

    public static CommandArgument choices(String name, String... values) {
        return new CommandArgument(name, ArgumentType.STRING, List.of(values), false);
    }

    public static CommandArgument player(String name) {
        return new CommandArgument(name, ArgumentType.PLAYER, List.of(), false);
    }

    public static CommandArgument repeatablePlayer(String name) {
        return new CommandArgument(name, ArgumentType.PLAYER, List.of(), true);
    }

    public static CommandArgument playerOrLiteral(String name, String... values) {
        return new CommandArgument(name, ArgumentType.PLAYER_OR_LITERAL, List.of(values), false);
    }

    public static CommandArgument restMessage(String name) {
        return new CommandArgument(name, ArgumentType.REST_MESSAGE, List.of(), false);
    }
}
