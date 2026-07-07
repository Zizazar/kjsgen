package com.zizazr.kjsgen.integration.net;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the two kjsgen payload channels and dispatches incoming payloads by op string.
 *
 * <p>The channel is registered {@code optional()} so a server or client that lacks kjsgen
 * simply drops the payloads instead of refusing the connection — that is what lets a
 * kjsgen client fall back to local-file mode against a vanilla/other-modded server.
 *
 * <p>Handlers run on the network thread by default, so every game-state mutation is wrapped
 * in {@link IPayloadContext#enqueueWork}. The client-bound handler is an explicit lambda (not
 * a method reference) referencing {@code ClientEditSession} only inside its body, so that
 * {@code Dist.CLIENT} class is never loaded on a dedicated server during registration.
 */
public final class KjsGenNet {
    // ---- op names (shared by both directions where it makes sense) ----
    public static final String OP_OPEN = "open";
    public static final String OP_CLOSE = "close";
    public static final String OP_LIST = "list";
    public static final String OP_CREATE = "create";
    public static final String OP_DELETE = "delete";
    public static final String OP_UPSERT_RECIPE = "upsertRecipe";
    public static final String OP_REMOVE_RECIPE = "removeRecipe";
    public static final String OP_META = "meta";
    public static final String OP_EXPORT = "export";
    /** Live mouse position of one editor (panel-relative). Both directions. */
    public static final String OP_CURSOR = "cursor";
    /** Which kjsgen screen an operator is currently on (for the presence tooltip). Both directions. */
    public static final String OP_SCREEN = "screen";
    // ---- server -> client only ----
    public static final String OP_SNAPSHOT = "snapshot";
    public static final String OP_EXPORT_RESULT = "exportResult";
    public static final String OP_DENIED = "denied";
    /** The set of operators currently viewing a project (+ their assigned colour). */
    public static final String OP_PRESENCE = "presence";

    private KjsGenNet() {
    }

    /** Mod-bus listener wired up from the {@code KjsGen} constructor. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(KjsGenC2SPayload.TYPE, KjsGenC2SPayload.CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer sender) {
                        ctx.enqueueWork(() -> ServerProjectStore.handle(sender, payload.op(), payload.json()));
                    }
                });
        registrar.playToClient(KjsGenS2CPayload.TYPE, KjsGenS2CPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(
                        () -> ClientEditSession.handleServer(payload.op(), payload.json())));
    }

    // ---- send helpers ----

    /** Client -> server. Call from client code only. */
    public static void toServer(String op, String json) {
        PacketDistributor.sendToServer(new KjsGenC2SPayload(op, json));
    }

    /** Server -> one client. */
    public static void toPlayer(ServerPlayer player, String op, String json) {
        PacketDistributor.sendToPlayer(player, new KjsGenS2CPayload(op, json));
    }
}
