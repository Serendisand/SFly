package com.serendisand.sfly.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.PlayerQuota;
import com.serendisand.sfly.data.QuotaType;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.flight.FlightService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.task.FlyTicker;
import com.serendisand.sfly.text.Durations;
import com.serendisand.sfly.text.Msg;
import com.serendisand.sfly.text.MessageService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 额度命令 {@code quota} + 强制飞行 {@code forcefly/forceunfly}。 */
final class QuotaCommands extends CommandSupport {
    /** 批量操作超过这个人数就只发一条汇总，免得刷屏 */
    private static final int PER_TARGET_FEEDBACK_LIMIT = 5;

    QuotaCommands(ConfigService configService, DataService dataService, QuotaService quotaService,
                  EventService eventService, FlightService flightService, MessageService messages) {
        super(configService, dataService, quotaService, eventService, flightService, messages);
    }

    LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("quota").requires(ADMIN)
                .then(operation("give", QuotaOp.GIVE))
                .then(operation("take", QuotaOp.TAKE))
                .then(operation("set", QuotaOp.SET))
                .then(Commands.literal("check")
                        .executes(ctx -> selfStatus(ctx))
                        .then(targetsArg().executes(ctx -> check(ctx, StringArgumentType.getString(ctx, "targets")))))
                .then(Commands.literal("reset")
                        .then(targetsArg().executes(ctx -> reset(ctx, StringArgumentType.getString(ctx, "targets")))))
                .then(Commands.literal("resetall").executes(this::resetAll))
                .then(Commands.literal("top")
                        .executes(ctx -> top(ctx, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> top(ctx, IntegerArgumentType.getInteger(ctx, "count")))));
    }

    LiteralArgumentBuilder<CommandSourceStack> buildForceFly() {
        return Commands.literal("forcefly").requires(ADMIN)
                .then(targetsArg().executes(ctx -> force(ctx, true)));
    }

    LiteralArgumentBuilder<CommandSourceStack> buildForceUnfly() {
        return Commands.literal("forceunfly").requires(ADMIN)
                .then(targetsArg().executes(ctx -> force(ctx, false)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> targetsArg() {
        return Commands.argument("targets", StringArgumentType.string()).suggests(TARGET_SUGGESTIONS);
    }

    private LiteralArgumentBuilder<CommandSourceStack> operation(String literal, QuotaOp op) {
        return Commands.literal(literal)
                .then(targetsArg()
                        .then(scope(op, "pausable", List.of(QuotaType.PAUSABLE)))
                        .then(scope(op, "fixed", List.of(QuotaType.FIXED)))
                        .then(scope(op, "all", List.of(QuotaType.PAUSABLE, QuotaType.FIXED))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> scope(QuotaOp op, String literal, List<QuotaType> types) {
        return Commands.literal(literal)
                .then(Commands.argument("amount", StringArgumentType.string())
                        .executes(ctx -> apply(ctx, op, types)));
    }

    private int apply(CommandContext<CommandSourceStack> ctx, QuotaOp op, List<QuotaType> types) {
        CommandSourceStack source = ctx.getSource();
        String rawTarget = StringArgumentType.getString(ctx, "targets");
        long millis = parseDuration(source, StringArgumentType.getString(ctx, "amount"));
        if (millis < 0L) {
            return 0;
        }
        List<Target> targets = TargetResolver.resolve(source, rawTarget);
        if (targets.isEmpty()) {
            messages.send(source, Msg.ADMIN_NO_TARGET, "target", rawTarget);
            return 0;
        }

        String typeLabel = describeTypes(types);
        String amount = Durations.format(millis);
        boolean verbose = targets.size() <= PER_TARGET_FEEDBACK_LIMIT;

        for (Target target : targets) {
            PlayerQuota quota = quotaService.getOrCreate(target.id(), target.name());
            for (QuotaType type : types) {
                switch (op) {
                    case GIVE -> quota.addBalance(type, millis);
                    case TAKE -> quota.addBalance(type, -millis);
                    case SET -> quota.setBalance(type, millis);
                }
            }
            // 被操作的玩家提醒一句（离线的收不到，正常）
            if (target.player() != null) {
                messages.send(target.player(), Msg.QUOTA_RECEIVED, FlyTicker.merge(
                        new Object[]{"amount", amount, "type", typeLabel},
                        FlightService.quotaPairs(quota)));
            }
            if (verbose) {
                messages.send(source, op.feedbackKey(), FlyTicker.merge(
                        new Object[]{"target", target.displayName(), "amount", amount,
                                "type", typeLabel, "count", targets.size()},
                        FlightService.quotaPairs(quota)));
            }
        }

        if (!verbose) {
            messages.send(source, op.summaryKey(), "count", targets.size(), "amount", amount, "type", typeLabel);
        }
        dataService.saveAsync();
        return targets.size();
    }

    private int check(CommandContext<CommandSourceStack> ctx, String rawTarget) {
        CommandSourceStack source = ctx.getSource();
        List<Target> targets = TargetResolver.resolve(source, rawTarget);
        if (targets.isEmpty()) {
            messages.send(source, Msg.ADMIN_NO_TARGET, "target", rawTarget);
            return 0;
        }
        messages.send(source, Msg.ADMIN_QUOTA_CHECK_HEADER);
        for (Target target : targets) {
            PlayerQuota quota = quotaService.get(target.id());
            if (quota == null) {
                messages.send(source, Msg.ADMIN_QUOTA_CHECK_LINE, "target", target.displayName(),
                        "pausable", "无记录", "fixed", "无记录", "total", "无记录",
                        "note", target.isOnline() ? "" : " &8(离线)");
                continue;
            }
            messages.send(source, Msg.ADMIN_QUOTA_CHECK_LINE, FlyTicker.merge(
                    new Object[]{"target", target.displayName(), "note", target.isOnline() ? "" : " &8(离线)"},
                    FlightService.quotaPairs(quota)));
        }
        return targets.size();
    }

    private int reset(CommandContext<CommandSourceStack> ctx, String rawTarget) {
        CommandSourceStack source = ctx.getSource();
        List<Target> targets = TargetResolver.resolve(source, rawTarget);
        if (targets.isEmpty()) {
            messages.send(source, Msg.ADMIN_NO_TARGET, "target", rawTarget);
            return 0;
        }
        for (Target target : targets) {
            PlayerQuota quota = quotaService.get(target.id());
            // clear() 会把 flightGranted 一起清掉，所以得先记下来再清，
            // 否则玩家会带着 mayfly 白飞（而且我们再也不会去收回）
            boolean wasGranted = quota != null && quota.isFlightGranted();
            quotaService.reset(target.id());
            if (wasGranted && target.player() != null) {
                flightService.revoke(target.player(), quota, Msg.QUOTA_EXHAUSTED);
            }
        }
        dataService.saveAsync();
        messages.send(source, Msg.ADMIN_QUOTA_RESET, "target", rawTarget, "count", targets.size());
        return targets.size();
    }

    private int resetAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        List<ServerPlayer> needRevoke = new ArrayList<>();
        if (server != null) {
            // 同样要先捞出「正在飞」的人，resetAll 之后就再也判断不出来了
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerQuota quota = quotaService.get(player.getUUID());
                if (quota != null && quota.isFlightGranted()) {
                    needRevoke.add(player);
                }
            }
        }
        int count = quotaService.resetAll();
        for (ServerPlayer player : needRevoke) {
            flightService.revoke(player, quotaService.get(player.getUUID()), Msg.QUOTA_EXHAUSTED);
        }
        dataService.saveAsync();
        messages.send(source, Msg.ADMIN_QUOTA_RESET_ALL, "count", count);
        return count;
    }

    private int top(CommandContext<CommandSourceStack> ctx, int limit) {
        CommandSourceStack source = ctx.getSource();
        List<Map.Entry<String, PlayerQuota>> entries = new ArrayList<>(dataService.data().players.entrySet());
        entries.removeIf(entry -> entry.getValue() == null || entry.getValue().total() <= 0L);
        entries.sort((a, b) -> Long.compare(b.getValue().total(), a.getValue().total()));

        if (entries.isEmpty()) {
            messages.send(source, Msg.ADMIN_QUOTA_TOP_EMPTY);
            return 0;
        }
        int shown = Math.min(limit, entries.size());
        messages.send(source, Msg.ADMIN_QUOTA_TOP_HEADER, "count", shown);
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, PlayerQuota> entry = entries.get(i);
            PlayerQuota quota = entry.getValue();
            String name = quota.getLastName();
            if (name == null || name.isEmpty()) {
                name = entry.getKey();
            }
            messages.send(source, Msg.ADMIN_QUOTA_TOP_LINE, FlyTicker.merge(
                    new Object[]{"index", i + 1, "target", name}, FlightService.quotaPairs(quota)));
        }
        return shown;
    }

    private int force(CommandContext<CommandSourceStack> ctx, boolean enable) {
        CommandSourceStack source = ctx.getSource();
        String rawTarget = StringArgumentType.getString(ctx, "targets");
        List<Target> targets = TargetResolver.resolve(source, rawTarget);
        if (targets.isEmpty()) {
            messages.send(source, Msg.ADMIN_NO_TARGET, "target", rawTarget);
            return 0;
        }
        int count = 0;
        for (Target target : targets) {
            if (target.player() == null) {
                continue;
            }
            PlayerQuota quota = quotaService.getOrCreate(target.id(), target.name());
            if (enable) {
                flightService.forceFly(target.player(), quota);
            } else {
                flightService.forceUnfly(target.player(), quota);
            }
            count++;
        }
        dataService.saveAsync();
        messages.send(source, enable ? Msg.ADMIN_FORCE_FLY : Msg.ADMIN_FORCE_UNFLY, "count", count);
        return count;
    }

    private static String describeTypes(List<QuotaType> types) {
        return types.size() > 1 ? "两种" : types.getFirst().displayName();
    }

    private enum QuotaOp {
        GIVE(Msg.ADMIN_QUOTA_GIVE, Msg.ADMIN_QUOTA_GIVE_MANY),
        TAKE(Msg.ADMIN_QUOTA_TAKE, Msg.ADMIN_QUOTA_TAKE_MANY),
        SET(Msg.ADMIN_QUOTA_SET, Msg.ADMIN_QUOTA_SET_MANY);

        private final String feedbackKey;
        private final String summaryKey;

        QuotaOp(String feedbackKey, String summaryKey) {
            this.feedbackKey = feedbackKey;
            this.summaryKey = summaryKey;
        }

        String feedbackKey() {
            return feedbackKey;
        }

        String summaryKey() {
            return summaryKey;
        }
    }
}
