package com.zizazr.kjsgen;

import com.mojang.logging.LogUtils;
import com.zizazr.kjsgen.api.RegisterRecipeTypesEvent;
import com.zizazr.kjsgen.codegen.CodegenRegistry;
import com.zizazr.kjsgen.integration.net.KjsGenNet;
import com.zizazr.kjsgen.integration.net.ServerProjectStore;
import com.zizazr.kjsgen.templates.BuiltinRecipeTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * KubeJS Generator — a dev tool that provides a JEI-like visual recipe editor
 * and exports the created recipes as KubeJS scripts.
 */
@Mod(KjsGen.MODID)
public class KjsGen {
    public static final String MODID = "kjsgen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KjsGen(IEventBus modEventBus, ModContainer modContainer) {
        CodegenRegistry.registerBuiltins();
        BuiltinRecipeTypes.register();
        modEventBus.addListener(this::commonSetup);
        // Multiplayer sync: register the payload channels (mod bus) and server-side viewer cleanup.
        modEventBus.addListener(KjsGenNet::register);
        NeoForge.EVENT_BUS.addListener(ServerProjectStore::onLogout);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Register the bundled JSON recipe types (Create/Mekanism layouts) on both physical sides.
        // The client also loads these via a resource-reload listener, but a dedicated server never
        // loads assets/, so without this the server registry would only hold the vanilla built-ins
        // and server-side export would silently drop every non-vanilla recipe.
        com.zizazr.kjsgen.templates.JsonLayoutLoader.loadBundled();
        // Let addon mods contribute their own recipe types and codegen handlers.
        net.neoforged.fml.ModLoader.postEvent(new RegisterRecipeTypesEvent());
        if (!isKubeJsLoaded()) {
            LOGGER.warn("KubeJS is not installed. Recipes can still be created and saved, "
                    + "but the exported scripts will only take effect once KubeJS is added to the instance.");
        }
    }

    public static boolean isKubeJsLoaded() {
        return ModList.get().isLoaded("kubejs");
    }

    public static boolean isJeiLoaded() {
        return ModList.get().isLoaded("jei");
    }
}
