package com.mythup.commandkit.mixin;

import com.mythup.commandkit.CommandCompletion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

/**
 * Adds registered local completions after vanilla has updated its suggestions.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow
    private EditBox input;

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
