package com.hypixelcommands.commandkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A literal command node. Nodes may have aliases, nested literals, and
 * positional arguments.
 */
public final class CommandNode {
    private final String name;
    private final List<String> names = new ArrayList<>();
    private final Map<String, CommandNode> children = new LinkedHashMap<>();
    private final List<CommandArgument> arguments = new ArrayList<>();
    private Predicate<CompletionContext> contextPredicate = context -> true;

    public CommandNode(String... names) {
        if (names == null || names.length == 0 || names[0] == null || names[0].isBlank()) {
            throw new IllegalArgumentException("A command needs at least one name");
        }
        this.name = names[0];
        for (String value : names) {
            alias(value);
        }
    }

    public static CommandNode literal(String name, String... aliases) {
        String[] names = new String[1 + (aliases == null ? 0 : aliases.length)];
        names[0] = name;
        if (aliases != null) {
            System.arraycopy(aliases, 0, names, 1, aliases.length);
        }
        return new CommandNode(names);
    }

    public CommandNode alias(String alias) {
        if (alias != null && !alias.isBlank() && !names.contains(alias)) {
            names.add(alias);
        }
        return this;
    }

    public CommandNode then(CommandNode child) {
        if (child == null) {
            throw new IllegalArgumentException("Child command cannot be null");
        }
        for (String lookupName : child.names) {
            children.put(lookupName.toLowerCase(Locale.ROOT), child);
        }
        return this;
    }

    public CommandNode argument(CommandArgument argument) {
        arguments.add(argument);
        return this;
    }

    /**
     * Restricts this command tree to a particular client context.
     */
    public CommandNode when(Predicate<CompletionContext> predicate) {
        contextPredicate = predicate == null ? context -> true : predicate;
        return this;
    }

    String primaryName() {
        return name;
    }

    List<String> names() {
        return names;
    }

    Map<String, CommandNode> children() {
        return children;
    }

    List<CommandArgument> arguments() {
        return arguments;
    }

    boolean isActive(CompletionContext context) {
        return contextPredicate.test(context);
    }
}
