package com.zizazr.kjsgen;

import com.mojang.blaze3d.platform.InputConstants;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.integration.net.ClientEditSession;
import com.zizazr.kjsgen.templates.JsonLayoutLoader;
import com.zizazr.kjsgen.templates.UserLayoutStore;
import com.zizazr.kjsgen.ui.vanilla.CollabCursors;
import com.zizazr.kjsgen.ui.vanilla.CollabScreens;
import com.zizazr.kjsgen.ui.vanilla.VanillaEditorScreen;
import com.zizazr.kjsgen.ui.vanilla.VanillaRemovalsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry: keybind + editor screen opening. The editor is a dev tool, so
 * it is gated to singleplayer / operators.
 */
@Mod(value = KjsGen.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KjsGen.MODID, value = Dist.CLIENT)
public class KjsGenClient {
    public static final Lazy<KeyMapping> OPEN_EDITOR = Lazy.of(() -> new KeyMapping(
            "key.kjsgen.open_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.kjsgen"
    ));

    public KjsGenClient(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(KjsGenClient::onClientTick);
        // Draw the other operators' cursors on top of everything (after the screen + its tooltips).
        NeoForge.EVENT_BUS.addListener(KjsGenClient::onScreenRenderPost);
        // A fresh connection starts in local mode until the server proves it has kjsgen.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> ClientEditSession.reset());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientEditSession.reset());
    }

    /** Top-most overlay: remote cursors float above the editor and any of its sub-dialogs. */
    static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (CollabScreens.isEditorContext(event.getScreen())) {
            CollabCursors.render(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR.get());
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new JsonLayoutLoader());
        // layouts previously imported from JEI plugins (survive restarts, JEI not required)
        UserLayoutStore.loadAll();
    }

    static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_EDITOR.get().consumeClick()) {
            openEditor();
        }
        // Keep the other operators' presence tooltip up to date with which screen we're on.
        ClientEditSession.tickScreenReport();
    }

    /** True when the local player may use the (dev-only) editor. */
    private static boolean mayEdit(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        if (!mc.hasSingleplayerServer() && !mc.player.hasPermissions(2)) {
            mc.player.displayClientMessage(Component.translatable("kjsgen.error.no_permission"), false);
            return false;
        }
        return true;
    }

    /** Opens the recipe editor if the local player is allowed to use it. */
    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (!mayEdit(mc)) {
            return;
        }
        mc.setScreen(new VanillaEditorScreen());
    }

    /**
     * Adds (or replaces the same type+output entry of) {@code recipe} in the current
     * project and opens the editor with it selected. Called from the JEI
     * "edit in kjsgen" recipe button.
     */
    public static void openEditorWithCapturedRecipe(RecipeInstance recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (!mayEdit(mc)) {
            return;
        }
        RecipeProject project = ClientEditSession.project();
        String uid = project.addOrReplaceByOutput(recipe);
        // In remote mode the recipe must reach the server before the editor requests its snapshot,
        // so push it first; the editor constructor then sends OP_OPEN and gets it back in the snapshot.
        ClientEditSession.pushRecipe(recipe);
        mc.setScreen(new VanillaEditorScreen(uid));
    }

    /**
     * Opens the removals screen ensuring a rule exists for {@code recipeId} (reusing an existing
     * one). The rule is added by the screen itself — and re-added after the editor's snapshot
     * arrives — so it survives the project being replaced when the session first goes remote.
     * Called from the JEI "remove with kjsgen" recipe button.
     */
    public static void openRemovalsForRecipeId(String recipeId) {
        Minecraft mc = Minecraft.getInstance();
        if (!mayEdit(mc)) {
            return;
        }
        // Show the editor first so it is initialized as the dialog's parent, then the dialog.
        VanillaEditorScreen editor = new VanillaEditorScreen();
        mc.setScreen(editor);
        mc.setScreen(VanillaRemovalsScreen.forRecipeId(editor, recipeId));
    }
}
