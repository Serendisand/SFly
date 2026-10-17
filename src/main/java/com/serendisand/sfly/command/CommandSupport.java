package com.serendisand.sfly.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.ActiveWindow;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.PlayerQuota;
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

import java.util.function.Predicate;

/**
 * 命令模块的共用底座：六个服务的引用 + 满世界都在用的解析/结算/取参工具。
 *
 * <p>拆类的目的只是「一个文件讲一件事」，不是分层 —— 子类直接继承这些成员，
 * 就别再套一层 Service 转发了。
 */
abstract class CommandSupport {
    /** 管理命令门槛。 */
    protected static final Predicate<CommandSourceStack> ADMIN =
            Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    protected final ConfigService configService;
    protected final DataService dataService;
    protected final QuotaService quotaService;
    protected final EventService eventService;
    protected final FlightService flightService;
    protected final MessageService messages;

    protected CommandSupport(ConfigService configService, DataService dataService, QuotaService quotaService,
                            EventService eventService, FlightService flightService, MessageService messages) {
        this.configService = configService;
        this.dataService = dataService;
        this.quotaService = quotaService;
        this.eventService = eventService;
        this.flightService = flightService;
        this.messages = messages;
    }

    /** 取执行者；控制台/命令方块会收到一句提示并拿到 null。 */
    protected ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            messages.send(ctx.getSource(), Msg.PLAYER_ONLY);
        }
        return player;
    }

    /** 时长解析失败时已经把错误发回去了，返回负数表示「别再往下走」。 */
    protected long parseDuration(CommandSourceStack source, String raw) {
        try {
            return Durations.parseMillis(raw);
        } catch (IllegalArgumentException e) {
            messages.send(source, Msg.ADMIN_BAD_DURATION, "error", e.getMessage());
            return -1L;
        }
    }

    /** 限免窗口内只拨基准不扣额度，其余时候正常结算。 */
    protected void settleNow(PlayerQuota quota, long now) {
        if (eventService.hasFreeActive(now)) {
            quotaService.freeze(quota, now);
        } else {
            quotaService.settle(quota, true, now);
        }
    }

    /** 参数没在语法树里就返回 null —— 用来区分「没写」和「写了个空串」。 */
    protected static String readArg(CommandContext<?> ctx, String name) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 同上，但没写时按 "0" 算。 */
    protected static String readArgOrEmpty(CommandContext<?> ctx, String name) {
        String value = readArg(ctx, name);
        return value == null ? "0" : value;
    }

    /**
     * 补全用：原版 {@code string()} 只认 {@code [0-9A-Za-z_.+-]}，
     * {@code @a}、{@code 20:00}、中文都得加引号。把引号一起补出去，
     * 玩家敲一半按 Tab 就能得到合法输入，不用背规则。
     */
    protected static String quoteIfNeeded(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (!isUnquotedSafe(raw.charAt(i))) {
                return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return raw;
    }

    /** 和 brigadier {@code StringReader.isAllowedInUnquotedString} 保持同一套字符集。 */
    private static boolean isUnquotedSafe(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || c == '_' || c == '-' || c == '.' || c == '+';
    }

    /** 玩家目标补全。带引号是因为选择器里的 {@code @} 不在原版 string 的字符集里。 */
    protected static final SuggestionProvider<CommandSourceStack> TARGET_SUGGESTIONS = (context, builder) -> {
        builder.suggest(quoteIfNeeded("@a"));
        builder.suggest(quoteIfNeeded("@p"));
        builder.suggest(quoteIfNeeded("@s"));
        builder.suggest(quoteIfNeeded("@r"));
        MinecraftServer server = context.getSource().getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                builder.suggest(quoteIfNeeded(player.getScoreboardName()));
            }
        }
        return builder.buildFuture();
    };

    /**
     * 「查自己」。
     *
     * <p>{@code /sf status} 和 {@code /sf quota check}（不带参数）都是这个 ——
     * 两边入口不同、内容是同一屏，所以放在底座上给两边共用。
     */
    protected int selfStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer self = source.getPlayer();
        if (self == null) {
            messages.send(source, Msg.PLAYER_ONLY);
            return 0;
        }
        PlayerQuota quota = quotaService.getOrCreate(self.getUUID(), self.getScoreboardName());
        settleNow(quota, System.currentTimeMillis());
        messages.send(self, Msg.STATUS, FlyTicker.merge(statusPairs(self), FlightService.quotaPairs(quota)));
        return 1;
    }

    /** 状态行里那几格跟额度无关、但要和额度一起发出去的占位符。 */
    protected Object[] statusPairs(ServerPlayer player) {
        ActiveWindow active = eventService.firstActive(System.currentTimeMillis());
        return new Object[]{
                "player", player.getScoreboardName(),
                "world", FlightService.dimensionId(player),
                "event", active == null ? "无" : active.name(),
        };
    }
}
