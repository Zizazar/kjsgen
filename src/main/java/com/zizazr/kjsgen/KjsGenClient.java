package com.zizazr.kjsgen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.mojang.blaze3d.platform.InputConstants;
import com.zizazr.kjsgen.core.ProjectManager;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.templates.JsonLayoutLoader;
import com.zizazr.kjsgen.templates.UserLayoutStore;
import com.zizazr.kjsgen.ui.KjsGenUI;
import com.zizazr.kjsgen.ui.vanilla.VanillaEditorScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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

    /** Opens the experimental LDLib2-free (pure vanilla Screen) editor for look comparison. */
    public static final Lazy<KeyMapping> OPEN_EDITOR_VANILLA = Lazy.of(() -> new KeyMapping(
            "key.kjsgen.open_editor_vanilla",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.kjsgen"
    ));

    public KjsGenClient(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(KjsGenClient::onClientTick);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR.get());
        event.register(OPEN_EDITOR_VANILLA.get());
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
        while (OPEN_EDITOR_VANILLA.get().consumeClick()) {
            openEditorVanilla();
        }
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
        KjsGenUI workspace = new KjsGenUI();
        ModularUI modularUI = new ModularUI(workspace.buildUI())
                .shouldCloseOnKeyInventory(false);
        mc.setScreen(new ModularUIScreen(modularUI, Component.translatable("kjsgen.title")));
    }

    /** Opens the pure-vanilla (LDLib2-free) editor screen. */
    public static void openEditorVanilla() {
        Minecraft mc = Minecraft.getInstance();
        if (!mayEdit(mc)) {
            return;
        }
        mc.setScreen(new VanillaEditorScreen());
    }

    /**
     * Adds (or replaces the same type+output entry of) {@code recipe} in the current
     * project and opens the vanilla editor with it selected. Called from the JEI
     * "edit in kjsgen" recipe button.
     */
    public static void openEditorWithCapturedRecipe(RecipeInstance recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (!mayEdit(mc)) {
            return;
        }
        RecipeProject project = ProjectManager.current();
        String uid = project.addOrReplaceByOutput(recipe);
        mc.setScreen(new VanillaEditorScreen(uid));
    }
}
