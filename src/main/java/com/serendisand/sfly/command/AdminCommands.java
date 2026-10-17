package com.serendisand.sfly.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.PlayerQuota;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.flight.FlightService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.text.Durations;
import com.serendisand.sfly.text.Msg;
import com.serendisand.sfly.text.MessageService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 运行状态与配置重载。 */
final class AdminCommands extends CommandSupport {

    AdminCommands(ConfigService configService, DataService dataService, QuotaService quotaService,
                  EventService eventService, FlightService flightService, MessageService messages) {
        super(configService, dataService, quotaService, eventService, flightService, messages);
    }

    LiteralArgumentBuilder<CommandSourceStack> buildReload() {
        return Commands.literal("reload").requires(ADMIN).executes(this::reload);
    }

    LiteralArgumentBuilder<CommandSourceStack> buildInfo() {
        return Commands.literal("info").executes(this::info);
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        configService.load();
        messages.send(ctx.getSource(), Msg.ADMIN_RELOADED);
        return 1;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        long now = System.currentTimeMillis();

        int online = server == null ? 0 : server.getPlayerList().getPlayerCount();
        int flying = 0;
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerQuota quota = quotaService.get(player.getUUID());
                if (quota != null && quota.isFlightGranted()) {
                    flying++;
                }
            }
        }
        messages.send(source, Msg.INFO,
                "flying_count", flying,
                "online", online,
                "records", dataService.data().players.size(),
                "event_count", eventService.allEvents().size(),
                "active_count", eventService.activeWindows(now).size(),
                "repeat_count", eventService.allRecurring().size(),
                "keep_finished", describeRetention(),
                "require_event", configService.get().flight.requireActiveEvent ? "是" : "否");
        return 1;
    }

    private String describeRetention() {
        long retention = eventService.retentionMillis();
        return retention < 0L ? "永久" : Durations.format(retention);
    }
}
