package com.example.pigabomination.progression;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * /pigabomination status       estado de la historia
 * /pigabomination reset        reinicia la historia
 * /pigabomination encounter    abominacion de acecho cerca de ti (prueba)
 * /pigabomination pig          cerdo de dia que te mira, delante de ti (prueba)
 * /pigabomination hunt         transformacion y pelea ahora mismo (prueba)
 * Requieren permiso de operador.
 */
public final class HorrorCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pigabomination").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    HorrorWorldData data = HorrorWorldData.get(server);
                    long time = server.overworld().getDayTime();
                    Component clock = Component.translatable(
                            HorrorNightManager.timeOfDay(time) >= HorrorNightManager.NIGHT_START
                                    ? "pigabomination.time.night" : "pigabomination.time.day");
                    Component phase = Component.translatable(switch (HorrorNightManager.phase(data)) {
                        case HUNT -> "pigabomination.phase.hunt";
                        case DEFEATED -> "pigabomination.phase.defeated";
                        default -> "pigabomination.phase.stalking";
                    });
                    ctx.getSource().sendSuccess(() -> Component.translatable("pigabomination.status",
                            HorrorNightManager.dayNumber(time), clock, data.getLastNight(), phase), false);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    HorrorWorldData.get(ctx.getSource().getServer()).reset();
                    ctx.getSource().sendSuccess(() -> Component.translatable("pigabomination.reset.done"), true);
                    return 1;
                }))
                .then(Commands.literal("encounter").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int night = Math.max(1, HorrorNightManager.startedNights(player.serverLevel().getDayTime()));
                    boolean ok = HorrorDirector.spawnStalker(player.serverLevel(), player, night, true);
                    if (ok) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("pigabomination.encounter.ok"), true);
                    } else {
                        ctx.getSource().sendFailure(Component.translatable("pigabomination.encounter.fail"));
                    }
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("pig").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean ok = HorrorDirector.spawnPig(player.serverLevel(), player, true);
                    if (ok) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("pigabomination.pig.ok"), true);
                    } else {
                        ctx.getSource().sendFailure(Component.translatable("pigabomination.encounter.fail"));
                    }
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("hunt").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean ok = HorrorDirector.transform(player.serverLevel(), player);
                    if (ok) {
                        HorrorWorldData.get(ctx.getSource().getServer()).markHuntStarted();
                        ctx.getSource().sendSuccess(() -> Component.translatable("pigabomination.hunt.ok"), true);
                    } else {
                        ctx.getSource().sendFailure(Component.translatable("pigabomination.hunt.fail"));
                    }
                    return ok ? 1 : 0;
                })));
    }

    private HorrorCommands() {}
}
