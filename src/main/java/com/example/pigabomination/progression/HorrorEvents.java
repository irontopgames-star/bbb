package com.example.pigabomination.progression;

import com.example.pigabomination.PigAbominationMod;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = PigAbominationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HorrorEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) {
            return;
        }
        HorrorNightManager.check(server);
    }

    /** Al cerrar el servidor (o salir de un mundo de un jugador) el director olvida lo que tenia en memoria. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HorrorDirector.reset();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        HorrorCommands.register(event.getDispatcher());
    }

    private HorrorEvents() {}
}
