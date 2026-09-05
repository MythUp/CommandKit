package com.mythup.commandkit;

import net.minecraft.client.Minecraft;

/**
 * Context supplied to command predicates while suggestions are generated.
 */
public final class CompletionContext {
    private final String input;
    private final Minecraft client;

    public CompletionContext(String input) {
        this(input, Minecraft.getInstance());
    }

    public CompletionContext(String input, Minecraft client) {
        this.input = input;
        this.client = client;
    }

    public String input() {
        return input;
    }

    public Minecraft client() {
        return client;
    }

    public boolean connected() {
        return client != null && client.level != null && client.player != null;
    }

    public String serverAddress() {
        if (client == null || client.getCurrentServer() == null) {
            return null;
        }
        return client.getCurrentServer().ip;
    }
}
