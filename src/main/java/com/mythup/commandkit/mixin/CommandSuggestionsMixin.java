package com.mythup.commandkit.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mythup.commandkit.CommandCompletion;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Renders locally registered CommandKit suggestions whenever vanilla computes
 * command suggestions, so they appear immediately while typing (no Tab needed).
 *
 * <p>Vanilla's auto-show callback runs before the end of {@code
 * updateCommandInfo}, so replacing {@code pendingSuggestions} at TAIL is not
 * enough on its own: the custom list must also be rendered explicitly.</p>
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
    private boolean commandsOnly;
    @Shadow
    @Final
    private boolean anchorToBottom;
    @Shadow
    private CommandSuggestions.SuggestionsList suggestions;

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void commandkit$onUpdateCommandInfo(CallbackInfo ci) {
        Suggestions custom = commandkit$customSuggestions();
        if (custom == null) {
            // Vanilla already rendered its own list during this update; leave it.
            return;
        }

        pendingSuggestions = CompletableFuture.completedFuture(custom);
        commandkit$showCustom(custom, false);
    }

    /**
     * Vanilla auto-show and Tab presses both funnel through showSuggestions;
     * reapply the local CommandKit suggestions at that point too so both
     * worlds coexist.
     */
    @Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
    private void commandkit$onShowSuggestions(boolean narrateFirstEntry, CallbackInfo ci) {
        Suggestions custom = commandkit$customSuggestions();
        if (custom == null) {
            return;
        }

        commandkit$showCustom(custom, narrateFirstEntry);
        ci.cancel();
    }

    private Suggestions commandkit$customSuggestions() {
        String inputText = input == null ? null : input.getValue();
        if (inputText == null || inputText.isEmpty()) {
            return null;
        }
        // Only override suggestions while a command is being typed; in plain
        // chat vanilla suggests tab-completion names instead.
        if (!commandsOnly && !inputText.startsWith("/")) {
            return null;
        }

        Suggestions custom = CommandCompletion.getSuggestions(inputText);
        return custom == null || custom.isEmpty() ? null : custom;
    }

    private void commandkit$showCustom(Suggestions custom, boolean narrateFirstEntry) {
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
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create the Minecraft command suggestions list", exception);
        }
    }
}
