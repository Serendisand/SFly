package com.serendisand.sfly.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.serendisand.sfly.SFlyConstants;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.flight.FlightService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.text.MessageService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * 命令树。根命令只有 {@code /sf}，不带参数就是飞行开关。
 *
 * <ul>
 *   <li>玩家侧：`/sf` 起飞关飞 ｜ on / off / pause / resume / status / bossbar / help</li>
 *   <li>管理侧：event / repeat / quota / forcefly / forceunfly / reload / info</li>
 * </ul>
 *
 * <p>这个类只负责搭骨架，具体 handler 在 {@link PlayerCommands}、{@link EventCommands}、
 * {@link QuotaCommands}、{@link AdminCommands} 里。
 */
public class SFlyCommands {
    private final PlayerCommands player;
    private final EventCommands event;
    private final QuotaCommands quota;
    private final AdminCommands admin;

    public SFlyCommands(ConfigService configService, DataService dataService, QuotaService quotaService,
                        EventService eventService, FlightService flightService, MessageService messages) {
        this.player = new PlayerCommands(configService, dataService, quotaService, eventService, flightService, messages);
        this.event = new EventCommands(configService, dataService, quotaService, eventService, flightService, messages);
        this.quota = new QuotaCommands(configService, dataService, quotaService, eventService, flightService, messages);
        this.admin = new AdminCommands(configService, dataService, quotaService, eventService, flightService, messages);
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildRoot());

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild(SFlyConstants.ROOT_COMMAND);
        SFlyConstants.LOGGER.info("✦ 命令注册完成：/{} ✔（{} 个子命令）",
                SFlyConstants.ROOT_COMMAND, root == null ? 0 : root.getChildren().size());
    }

    // ================================================================
    // /sf
    // ================================================================

    private LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        return Commands.literal(SFlyConstants.ROOT_COMMAND)
                // 不带参数就是飞行开关，根命令就是用来飞的，帮助走 /sf help
                .executes(player::toggle)
                .then(player.buildHelp())
                .then(Commands.literal("on").executes(player::takeOff))
                .then(Commands.literal("off").executes(player::land))
                .then(Commands.literal("pause").executes(player::pause))
                .then(Commands.literal("resume").executes(player::resume))
                .then(Commands.literal("status")
                        .executes(ctx -> player.status(ctx, null))
                        .then(Commands.argument("target", StringArgumentType.string())
                                .suggests(CommandSupport.TARGET_SUGGESTIONS)
                                .requires(CommandSupport.ADMIN)
                                .executes(ctx -> player.status(ctx, StringArgumentType.getString(ctx, "target")))))
                .then(Commands.literal("bossbar")
                        .executes(ctx -> player.bossBar(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> player.bossBar(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> player.bossBar(ctx, false))))
                .then(admin.buildInfo())
                .then(admin.buildReload())
                .then(event.build())
                .then(event.buildRepeat())
                .then(quota.build())
                .then(quota.buildForceFly())
                .then(quota.buildForceUnfly());
    }
}
