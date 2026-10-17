package com.serendisand.sfly.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.data.PlayerQuota;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.flight.FlightService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.task.FlyTicker;
import com.serendisand.sfly.text.Msg;
import com.serendisand.sfly.text.MessageService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** 玩家侧：起飞、降落、暂停、查状态。 */
final class PlayerCommands extends CommandSupport {

    PlayerCommands(ConfigService configService, DataService dataService, QuotaService quotaService,
                   EventService eventService, FlightService flightService, MessageService messages) {
        super(configService, dataService, quotaService, eventService, flightService, messages);
    }

    /** 不带参数就是飞行开关 —— 根命令就是用来飞的，帮助走 /sf help。 */
    int toggle(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());
        // 先结算一次，否则玩家看到的是最多 1 秒前的余额
        settleNow(quota, now);
        return flightService.toggle(player, quota, now) ? 1 : 0;
    }

    /** {@code /sf on}：只起飞，绝不把人按下来。 */
    int takeOff(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());
        settleNow(quota, now);
        return flightService.takeOff(player, quota, now) ? 1 : 0;
    }

    int land(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());
        settleNow(quota, now);
        // 连 mayfly 一起收回 —— 只放下人的话双击空格又能飞，等于没关
        flightService.turnOff(player, quota);
        return 1;
    }

    int pause(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());
        if (quota.isPaused()) {
            messages.send(player, Msg.PAUSE_ALREADY);
            return 0;
        }
        // 只有可暂停额度才谈得上暂停；全是不可暂停额度的话暂停没有意义
        if (quota.getPausableMillis() <= 0L) {
            messages.send(player, Msg.PAUSE_UNAVAILABLE);
            return 0;
        }
        settleNow(quota, System.currentTimeMillis());
        quota.setPaused(true);
        // 暂停 = 冻结额度 = 不能再飞。正在飞的话直接收回（空中会给缓降保护），
        // 否则玩家就能靠暂停实现免费无限飞行
        if (quota.isFlightGranted()) {
            flightService.revoke(player, quota, null);
        }
        messages.send(player, Msg.PAUSE_ON, FlightService.quotaPairs(quota));
        return 1;
    }

    int resume(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());
        if (!quota.isPaused()) {
            messages.send(player, Msg.RESUME_ALREADY);
            return 0;
        }
        // 必须先结算再解除暂停：暂停只挡得住可暂停额度，不可暂停那部分在这期间照样该扣。
        // settle 内部会看 isPaused()，所以此刻不会误扣可暂停额度。
        settleNow(quota, System.currentTimeMillis());
        quota.setPaused(false);
        messages.send(player, Msg.PAUSE_OFF, FlightService.quotaPairs(quota));
        return 1;
    }

    /** {@code /sf bossbar [on|off]}：玩家自己决定看不看额度 BossBar，不带参数 = 翻转。 */
    int bossBar(CommandContext<CommandSourceStack> ctx, Boolean wanted) {
        ServerPlayer player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        PlayerQuota quota = quotaService.getOrCreate(player.getUUID(), player.getScoreboardName());
        // 没写参数时：当前是藏着的 → 这次要显示；当前显示着 → 这次要藏
        boolean show = wanted == null ? quota.isBossBarHidden() : wanted;
        quota.setBossBarHidden(!show);
        if (!show) {
            // 立刻收掉，不用等下一轮 HUD tick
            messages.hideBossBar(player);
        }
        messages.send(player, show ? Msg.BOSS_BAR_ON : Msg.BOSS_BAR_OFF);
        dataService.saveAsync();
        return 1;
    }

    /** {@code rawTarget == null} 是查自己，非 null 是管理员查别人。 */
    int status(CommandContext<CommandSourceStack> ctx, String rawTarget) {
        CommandSourceStack source = ctx.getSource();

        if (rawTarget == null) {
            return selfStatus(ctx);
        }

        List<Target> targets = TargetResolver.resolve(source, rawTarget);
        if (targets.isEmpty()) {
            messages.send(source, Msg.ADMIN_NO_TARGET, "target", rawTarget);
            return 0;
        }
        for (Target target : targets) {
            PlayerQuota quota = quotaService.get(target.id());
            if (quota == null) {
                messages.send(source, Msg.STATUS_NONE);
                continue;
            }
            if (target.player() != null) {
                messages.send(source, Msg.STATUS,
                        FlyTicker.merge(statusPairs(target.player()), FlightService.quotaPairs(quota)));
            } else {
                messages.send(source, Msg.STATUS_OTHER, FlyTicker.merge(
                        new Object[]{"player", target.displayName()}, FlightService.quotaPairs(quota)));
            }
        }
        return targets.size();
    }

    int help(CommandContext<CommandSourceStack> ctx, boolean admin) {
        CommandSourceStack source = ctx.getSource();
        messages.sendLines(source, configService.get().helpPlayer);
        if (admin && ADMIN.test(source)) {
            messages.sendLines(source, configService.get().helpAdmin);
        }
        return 1;
    }

    LiteralArgumentBuilder<CommandSourceStack> buildHelp() {
        return Commands.literal("help")
                .executes(ctx -> help(ctx, false))
                .then(Commands.literal("admin").requires(ADMIN).executes(ctx -> help(ctx, true)));
    }
}
