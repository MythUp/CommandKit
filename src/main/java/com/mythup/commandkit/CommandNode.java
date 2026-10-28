package com.mythup.commandkit;

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
    private String serverIpPattern = null;

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

    /** Lists the command aliases for this node. */
    List<String> names() {
        return names;
    }

    /** Returns the arguments of this command node. */
    List<CommandArgument> arguments() {
        return arguments;
    }

    /** Returns the children of this command node. */
    Map<String, CommandNode> children() {
        return children;
    }

    /** The primary name of this command node. */
    String primaryName() {
        return name;
    }

    /** Sets the server IP pattern for this command node (e.g. "hypixel.net", "mccentral.org"). */
    public CommandNode serverIp(String ipPattern) {
        this.serverIpPattern = ipPattern;
        return this;
    }

    /** Checks if the current server IP matches the configured pattern. */
    private boolean matchesServerIpPattern(CompletionContext context) {
        if (serverIpPattern == null) {
            return true; // no pattern = always active
        }
        String currentIp = context.serverAddress();
        if (currentIp == null) {
            return false; // no server = not active
        }
        String lowerIp = currentIp.toLowerCase(Locale.ROOT);
        return lowerIp.contains(serverIpPattern.toLowerCase(Locale.ROOT));
    }

    /** Checks if this command node is active for the given completion context. */
    boolean isActive(CompletionContext context) {
        boolean predicateActive = contextPredicate.test(context);
        boolean serverMatches = matchesServerIpPattern(context);
        return predicateActive && serverMatches;
    }

    /** Restricts this command tree to a particular client context. */
    public CommandNode when(Predicate<CompletionContext> predicate) {
        contextPredicate = predicate == null ? context -> true : predicate;
        return this;
    }

    public CommandNode then(CommandNode child) {
        if (child == null) {
            throw new IllegalArgumentException("Child command cannot be null");
        } else {
            for (String lookupName : child.names) {
                this.children.put(lookupName.toLowerCase(Locale.ROOT), child);
            }
            return this;
        }
    }

    public CommandNode then(CommandNode child, CommandArgument argument) {
        if (child == null) {
            throw new IllegalArgumentException("Child command cannot be null");
        }
        this.then(child);

        if (argument != null) {
            child.argument(argument);
        }

        return this;
    }

    public CommandNode then(CommandNode child, CommandArgument... arguments) {
        this.then(child);
        if (arguments != null) {
            for (CommandArgument arg : arguments) {
                if (arg != null) {
                    child.argument(arg);
                }
            }
        }
        return this;
    }

    public CommandNode argument(CommandArgument argument) {
        arguments.add(argument);
        return this;
    }
}