package com.mythup.commandkit.mixin;

import com.mythup.commandkit.CommandCompletion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Adds registered local completions after vanilla has updated its suggestions.
 */
@Mixin(value = CommandSuggestions.class, priority = 500)
public abstract class CommandSuggestionsMixin {
    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow
    private EditBox input;
    @Shadow
    @Final
    private Font font;
    @Shadow
    @Final
    private Screen screen;
    @Shadow
    @Final
    private boolean anchorToBottom;
    @Shadow
    private CommandSuggestions.SuggestionsList suggestions;

    private boolean commandCompletionOverride;

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void onUpdateCommandInfo(CallbackInfo ci) {
        String inputText = input == null ? null : input.getValue();
        if (inputText == null || inputText.isEmpty()) {
            commandCompletionOverride = false;
            return;
        }
        if (!inputText.startsWith("/")) {
            inputText = "/" + inputText;
        }

        Suggestions custom = CommandCompletion.getSuggestions(inputText);
        if (custom == null || custom.isEmpty()) {
            if (commandCompletionOverride) {
                pendingSuggestions = Suggestions.empty();
                clearVanillaSuggestions();
                commandCompletionOverride = false;
            }
            return;
        }

        pendingSuggestions = CompletableFuture.completedFuture(custom);
        commandCompletionOverride = true;
    }

    /**
     * Smart Completion replaces updateCommandInfo and later renders its own
     * aggregated list. Reapply local CommandKit suggestions at the final
     * rendering point so both mods can coexist.
     */
    @Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
    private void onShowSuggestions(boolean narrateFirstEntry, CallbackInfo ci) {
        String inputText = input == null ? null : input.getValue();
        if (inputText == null || inputText.isEmpty()) {
            return;
        }
        if (!inputText.startsWith("/")) {
            inputText = "/" + inputText;
        }

        Suggestions custom = CommandCompletion.getSuggestions(inputText);
        if (custom == null || custom.isEmpty()) {
            return;
        }

        int width = custom.getList().stream()
                .mapToInt(suggestion -> font.width(suggestion.getText()))
                .max()
                .orElse(0);
        int left = Mth.clamp(
                input.getScreenX(custom.getRange().getStart()),
                0,
                input.getScreenX(0) + input.getInnerWidth() - width
        );
        int anchor = anchorToBottom ? screen.height - 12 : 72;

        try {
            Constructor<CommandSuggestions.SuggestionsList> constructor =
                    CommandSuggestions.SuggestionsList.class.getDeclaredConstructor(
                            CommandSuggestions.class,
                            int.class,
                            int.class,
                            int.class,
                            List.class,
                            boolean.class
                    );
            constructor.setAccessible(true);
            suggestions = constructor.newInstance(
                    (CommandSuggestions) (Object) this,
                    left,
                    anchor,
                    width,
                    custom.getList(),
                    narrateFirstEntry
            );
            ci.cancel();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create the Minecraft command suggestions list", exception);
        }
    }

    private void clearVanillaSuggestions() {
        try {
            Field suggestionsField = CommandSuggestions.class.getDeclaredField("suggestions");
            suggestionsField.setAccessible(true);
            suggestionsField.set((CommandSuggestions) (Object) this, null);
        } catch (ReflectiveOperationException ignored) {
            // The pending future is still cleared on mappings where this field is absent.
        }
    }
}
