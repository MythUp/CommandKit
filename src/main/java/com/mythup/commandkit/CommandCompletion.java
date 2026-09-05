package com.mythup.commandkit;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registry and suggestion engine for client-side command definitions.
 */
public final class CommandCompletion {
    private static final Map<String, CommandNode> ROOTS = new LinkedHashMap<>();

    private CommandCompletion() {
    }

    /**
     * Registers a top-level command and all its aliases.
     */
    public static synchronized void register(CommandNode command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        for (String name : command.names()) {
            ROOTS.put(name.toLowerCase(Locale.ROOT), command);
        }
    }

    public static synchronized void clear() {
        ROOTS.clear();
    }

    public static Suggestions getSuggestions(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String raw = input.startsWith("/") ? input : "/" + input;
        String trimmed = raw.substring(1).trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        CompletionContext context = new CompletionContext(raw);
        String[] tokens = trimmed.split("\\s+");
        boolean endsWithSpace = Character.isWhitespace(raw.charAt(raw.length() - 1));
        CommandNode current = null;
        int commandDepth = 0;
        boolean commandPathMatched = true;
        boolean partialCommandToken = false;

        for (String token : tokens) {
            if (current == null) {
                CommandNode root = ROOTS.get(token.toLowerCase(Locale.ROOT));
                if (root == null) {
                    partialCommandToken = !endsWithSpace && token.equals(tokens[tokens.length - 1]);
                    commandPathMatched = false;
                    break;
                }
                current = root;
            } else {
                CommandNode child = current.children().get(token.toLowerCase(Locale.ROOT));
                if (child == null) {
                    partialCommandToken = !endsWithSpace && token.equals(tokens[tokens.length - 1]);
                    commandPathMatched = false;
                    break;
                }
                current = child;
            }
            commandDepth++;
        }

        if (current != null && !current.isActive(context)) {
            return null;
        }

        String currentWord = endsWithSpace ? "" : tokens[tokens.length - 1];
        int consumedArgs = endsWithSpace
                ? Math.max(0, tokens.length - commandDepth)
                : Math.max(0, tokens.length - commandDepth - 1);

        if (current != null) {
            boolean consumedArgumentsValid = argumentsCanConsume(current.arguments(), consumedArgs);
            boolean argumentSlotAvailable = argumentSlotAvailable(
                    current.arguments(),
                    consumedArgs,
                    endsWithSpace
            );
            if (!commandPathMatched && !partialCommandToken
                    && (!consumedArgumentsValid || !argumentSlotAvailable)) {
                return null;
            }

            int argumentIndex = resolveArgumentIndex(current.arguments(), consumedArgs, endsWithSpace);
            Suggestions arguments = null;
            if (argumentIndex >= 0 && argumentIndex < current.arguments().size()) {
                arguments = parameterSuggestions(raw, current.arguments().get(argumentIndex), currentWord, context);
            }
            Suggestions children = consumedArgs == 0 && (commandPathMatched || partialCommandToken)
                    ? childSuggestions(current, currentWord, raw, context)
                    : null;
            if (arguments != null && children != null) {
                return Suggestions.merge(raw, List.of(arguments, children));
            }
            if (arguments != null) {
                return arguments;
            }
            return children;
        }

        if (current == null) {
            if (tokens.length > 1 && !partialCommandToken || endsWithSpace) {
                return null;
            }
            return rootSuggestions(currentWord, raw, context);
        }
        return childSuggestions(current, currentWord, raw, context);
    }

    private static boolean argumentsCanConsume(List<CommandArgument> arguments, int consumedArgs) {
        if (consumedArgs <= arguments.size()) {
            return true;
        }
        return !arguments.isEmpty() && arguments.get(arguments.size() - 1).repeatable();
    }

    private static boolean argumentSlotAvailable(
            List<CommandArgument> arguments,
            int consumedArgs,
            boolean endsWithSpace
    ) {
        if (arguments.isEmpty()) {
            return false;
        }
        if (!endsWithSpace && consumedArgs < arguments.size()) {
            return true;
        }
        return arguments.get(arguments.size() - 1).repeatable()
                && consumedArgs >= arguments.size() - 1;
    }

    private static int resolveArgumentIndex(
            List<CommandArgument> arguments,
            int consumedArgs,
            boolean endsWithSpace
    ) {
        if (arguments.isEmpty()) {
            return -1;
        }
        int lastIndex = arguments.size() - 1;
        if (endsWithSpace) {
            if (arguments.get(lastIndex).repeatable()) {
                return Math.min(consumedArgs, lastIndex);
            }
            return consumedArgs < arguments.size() ? consumedArgs : -1;
        }
        if (consumedArgs <= 0) {
            return 0;
        }
        return Math.min(consumedArgs, lastIndex);
    }

    private static Suggestions rootSuggestions(String currentWord, String raw, CompletionContext context) {
        List<Suggestion> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String prefix = currentWord.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, CommandNode> entry : ROOTS.entrySet()) {
            String name = entry.getKey();
            if (seen.add(name)
                    && entry.getValue().isActive(context)
                    && name.startsWith(prefix)) {
                suggestions.add(new Suggestion(range(raw), name));
            }
        }
        return suggestions.isEmpty() ? null : new Suggestions(range(raw), suggestions);
    }

    private static Suggestions childSuggestions(
            CommandNode node,
            String currentWord,
            String raw,
            CompletionContext context
    ) {
        List<Suggestion> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String prefix = currentWord.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, CommandNode> entry : node.children().entrySet()) {
            String childName = entry.getKey();
            if (seen.add(childName)
                    && entry.getValue().isActive(context)
                    && childName.startsWith(prefix)) {
                suggestions.add(new Suggestion(range(raw), childName));
            }
        }
        return suggestions.isEmpty() ? null : new Suggestions(range(raw), suggestions);
    }

    private static Suggestions parameterSuggestions(
            String raw,
            CommandArgument argument,
            String currentWord,
            CompletionContext context
    ) {
        if (argument.type() == ArgumentType.REST_MESSAGE) {
            return null;
        }
        List<String> values = valuesFor(argument, context);
        List<Suggestion> suggestions = new ArrayList<>();
        String prefix = currentWord.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(new Suggestion(range(raw), value));
            }
        }
        return suggestions.isEmpty() ? null : new Suggestions(range(raw), suggestions);
    }

    private static List<String> valuesFor(CommandArgument argument, CompletionContext context) {
        List<String> values = new ArrayList<>();
        if (argument.type() == ArgumentType.STRING) {
            values.addAll(argument.literals());
        }
        if (argument.type() == ArgumentType.PLAYER || argument.type() == ArgumentType.PLAYER_OR_LITERAL) {
            values.addAll(playerNames(context.client()));
        }
        if (argument.type() == ArgumentType.PLAYER_OR_LITERAL) {
            values.addAll(argument.literals());
        }
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return values.stream().distinct().toList();
    }

    private static List<String> playerNames(Minecraft client) {
        if (client == null || client.getConnection() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (PlayerInfo playerInfo : client.getConnection().getOnlinePlayers()) {
            String name = profileName(playerInfo);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static String profileName(PlayerInfo playerInfo) {
        try {
            Object profile = playerInfo.getProfile();
            if (profile == null) {
                return null;
            }
            try {
                Method method = profile.getClass().getMethod("getName");
                Object value = method.invoke(profile);
                return value instanceof String string ? string : null;
            } catch (NoSuchMethodException ignored) {
                Field field = profile.getClass().getDeclaredField("name");
                field.setAccessible(true);
                Object value = field.get(profile);
                return value instanceof String string ? string : null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static StringRange range(String raw) {
        int start = raw.lastIndexOf(' ') + 1;
        if (start <= 0) {
            start = 1;
        }
        return StringRange.between(start, raw.length());
    }
}
