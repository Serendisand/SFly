package com.serendisand.sfly.flight;

import com.serendisand.sfly.SFlyConstants;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.config.SFlyConfig;
import com.serendisand.sfly.data.PlayerQuota;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.text.Durations;
import com.serendisand.sfly.text.Msg;
import com.serendisand.sfly.text.MessageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;

/**
 * 飞行权限的授予与回收。
 *
 * <p><b>绝不让 mayfly 常驻。</b>原版 {@code Player#causeFallDamage} 的第一行就是
 * {@code if (abilities.mayfly) return false;} —— mayfly 一旦挂上，这个玩家就白嫖了摔落免疫。
 * 所以规矩是：
 * <ul>
 *   <li>只有玩家<b>真的按了 /sf 起飞</b>那一刻才置 mayfly</li>
 *   <li>落地、会话超时、额度耗尽、活动结束、主动关飞、离线 —— 任一发生就立刻收回
 *       （受伤默认<b>不</b>断飞，想防打架逃跑就把 {@code flight.disableOnDamage} 打开）</li>
 *   <li>没起过飞的玩家、创造/旁观玩家，我们一根手指都不碰</li>
 * </ul>
 * 保留 mayfly 的唯一窗口是「起飞之后、落地之前」，正好对上原版创造的手感：
 * 双击空格关掉飞行往下掉，不掉血。
 */
public class FlightService {
    /** 原版创造模式的飞行速度 */
    private static final double BASE_FLY_SPEED = 0.05D;

    private final ConfigService configService;
    private final QuotaService quotaService;
    private final EventService eventService;
    private final MessageService messages;

    public FlightService(ConfigService configService, QuotaService quotaService,
                         EventService eventService, MessageService messages) {
        this.configService = configService;
        this.quotaService = quotaService;
        this.eventService = eventService;
        this.messages = messages;
    }

    /** 不能起飞的原因，直接对应要发哪条文案。 */
    public enum Block {
        NONE, GAMEMODE, NO_QUOTA, PAUSED, NO_EVENT, WORLD, HEIGHT
    }

    public Block check(ServerPlayer player, PlayerQuota quota, long now) {
        if (player.isCreative() || player.isSpectator()) {
            return Block.GAMEMODE;
        }
        // 限免窗口：不用也不扣额度，所以「没额度」和「暂停中」两条门槛都让开
        if (!eventService.hasFreeActive(now)) {
            if (quota == null || !quota.hasAny()) {
                return Block.NO_QUOTA;
            }
            // 暂停 = 额度冻结，冻结期间当然也不该能飞 ——
            // 否则「暂停」就成了免费无限飞行，这条不能省。
            if (!hasDrainingQuota(quota)) {
                return Block.PAUSED;
            }
        }
        SFlyConfig.Flight flight = configService.get().flight;
        if (flight.isWorldBlocked(dimensionId(player))) {
            return Block.WORLD;
        }
        double y = player.getY();
        if (flight.maxY > 0 && y > flight.maxY) {
            return Block.HEIGHT;
        }
        if (flight.minY > -1 && y < flight.minY) {
            return Block.HEIGHT;
        }
        if (flight.requireActiveEvent && !eventService.hasActive(now)) {
            return Block.NO_EVENT;
        }
        return Block.NONE;
    }

    /**
     * 玩家现在有没有「正在走表」的额度。
     *
     * <p>注意只有<b>手动</b>暂停才算冻结；离线导致的暂停不算 ——
     * 那是可暂停额度的福利，玩家上线后照样该能飞。
     */
    public boolean hasDrainingQuota(PlayerQuota quota) {
        if (quota == null) {
            return false;
        }
        if (quota.getFixedMillis() > 0L) {
            return true;
        }
        return quota.getPausableMillis() > 0L && !quota.isPaused();
    }

    /**
     * 玩家执行 /sf。
     *
     * @return 操作后是否处于飞行状态
     */
    /**
     * 只负责起飞，绝不降落。已经飞着就只回显状态。
     * 绑快捷键/写宏的时候用这个，行为是确定的。
     */
    public boolean takeOff(ServerPlayer player, PlayerQuota quota, long now) {
        // 创造/旁观的原版飞行不归我们管，一根手指都不碰
        if (player.isCreative() || player.isSpectator()) {
            messages.send(player, Msg.FLY_BLOCKED_GAMEMODE);
            return player.getAbilities().flying;
        }
        if (player.getAbilities().flying) {
            messages.send(player, Msg.FLY_ON, quotaPairs(quota));
            return true;
        }

        Block block = check(player, quota, now);
        if (block != Block.NONE) {
            messages.send(player, blockMessage(block), quotaPairs(quota));
            return false;
        }

        grantAbilities(player, quota);
        messages.send(player, Msg.FLY_ON, quotaPairs(quota));
        return true;
    }

    /** {@code /sf} 的智能开关：飞着就关，没飞就起。 */
    public boolean toggle(ServerPlayer player, PlayerQuota quota, long now) {
        if (player.getAbilities().flying && !player.isCreative() && !player.isSpectator()) {
            turnOff(player, quota);
            return false;
        }
        return takeOff(player, quota, now);
    }

    /**
     * 玩家主动关闭飞行（{@code /sf} / {@code /sf off}）。
     *
     * <p>必须连 mayfly 一起收回 —— 服务端 {@code handlePlayerAbilities} 只认
     * {@code mayfly}，只关 flying 的话下一次双击空格就又飞起来了，等于没关。
     */
    public void turnOff(ServerPlayer player, PlayerQuota quota) {
        if (player.isCreative() || player.isSpectator()) {
            messages.send(player, Msg.FLY_BLOCKED_GAMEMODE);
            return;
        }
        // 只收我们授过的权限，别的插件给的飞行不去碰
        if (quota != null && quota.isFlightGranted()) {
            revoke(player, quota, Msg.FLY_OFF);
            return;
        }
        if (player.getAbilities().flying) {
            land(player);
        }
        messages.send(player, Msg.FLY_OFF, quotaPairs(quota));
    }

    /** 管理员强制开启，跳过额度/活动检查。 */
    public void forceFly(ServerPlayer player, PlayerQuota quota) {
        grantAbilities(player, quota);
        if (quota != null) {
            // 关键：不打这个标记的话，下一 tick 的 maintain 就会因为「你没额度」把飞行收回去，
            // 强制飞行只能活一个 tick
            quota.setFlightForced(true);
        }
        messages.send(player, Msg.FORCED_FLY_ON);
    }

    /** 管理员强制关闭。 */
    public void forceUnfly(ServerPlayer player, PlayerQuota quota) {
        revoke(player, quota, Msg.FORCED_FLY_OFF);
    }

    /**
     * 只关掉 flying、保留 mayfly。只用在「飞行权限不是 SFly 授的」这种边角情况 ——
     * 我们自己授的会话一律走 {@link #revoke}，不然双击空格又能飞回来。
     */
    public void land(ServerPlayer player) {
        try {
            Abilities abilities = player.getAbilities();
            abilities.flying = false;
            player.onUpdateAbilities();
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("关闭 {} 的飞行失败", player.getScoreboardName(), e);
        }
    }

    /** 玩家上线时清掉上次会话的残留，一切重新算。 */
    public void clearSessionOnJoin(ServerPlayer player, PlayerQuota quota) {
        if (quota == null || !quota.isFlightGranted()) {
            return;
        }
        setAbilities(player, false);
        quota.setFlightGranted(false);
        quota.setFlightForced(false);
        quota.setAirborneSince(-1L);
    }

    /** 玩家离线时同上，顺带把这次在线期间的额度结算掉。 */
    public void onDisconnect(ServerPlayer player, PlayerQuota quota, long now) {
        if (quota != null) {
            quotaService.settle(quota, true, now);
            quota.setFlightGranted(false);
            quota.setFlightForced(false);
            quota.setAirborneSince(-1L);
        }
        setAbilities(player, false);
    }

    /** 受伤断飞（{@code flight.disableOnDamage}，默认关闭）。开了的话很多服拿它防止打架途中逃跑。 */
    public void onDamage(ServerPlayer player, PlayerQuota quota) {
        if (quota == null || !quota.isFlightGranted()) {
            return;
        }
        if (!configService.get().flight.disableOnDamage) {
            return;
        }
        revoke(player, quota, Msg.FLY_DISABLED_BY_DAMAGE);
    }

    /**
     * 每 tick 的会话维持：条件没了就收回，落地或超时就结束会话。
     * 这个方法可以随便调 —— 它保证「不满足条件 ⇒ 没有 mayfly」。
     */
    public void maintain(ServerPlayer player, PlayerQuota quota, long now) {
        if (quota == null || !quota.isFlightGranted()) {
            return;
        }

        // 玩家中途变成创造/旁观，飞行权限归原版管，我们退场
        if (player.isCreative() || player.isSpectator()) {
            quota.setFlightGranted(false);
            quota.setFlightForced(false);
            quota.setAirborneSince(-1L);
            return;
        }

        // 管理员强开的飞行不受额度/活动约束，但仍然只活一个架次：落地就结束
        if (quota.isFlightForced()) {
            if (!player.getAbilities().flying && player.onGround()) {
                endSession(player, quota);
            } else {
                quota.setAirborneSince(-1L);
            }
            return;
        }

        Block block = check(player, quota, now);
        if (block != Block.NONE) {
            revoke(player, quota, revokeReason(block));
            return;
        }

        if (player.getAbilities().flying) {
            quota.setAirborneSince(-1L);
            return;
        }

        // 没在飞。
        if (player.onGround()) {
            if (configService.get().flight.revokeOnLanding) {
                // 落地就把无伤状态还回去，保证没在飞的人一律正常吃摔落伤害
                endSession(player, quota);
            } else {
                // 还原版创造手感：本次「飞行会话」一直留着，直到额度/活动结束或离线
                quota.setAirborneSince(-1L);
            }
            return;
        }
        if (quota.getAirborneSince() < 0L) {
            quota.setAirborneSince(now);
            return;
        }
        long grace = Math.max(0, configService.get().flight.airSessionGraceSeconds) * 1000L;
        if (now - quota.getAirborneSince() > grace) {
            endSession(player, quota);
        }
    }

    /** 强制收回飞行，必要时给缓降保护。 */
    public void revoke(ServerPlayer player, PlayerQuota quota, String reasonKey) {
        boolean airborne = !player.onGround();
        setAbilities(player, false);
        if (quota != null) {
            quota.setFlightGranted(false);
            quota.setFlightForced(false);
            quota.setAirborneSince(-1L);
        }

        boolean protectedBySlowFall = airborne && configService.get().flight.safeLanding && applySlowFalling(player);
        // 放在发文案之前 —— 文案本身要是配成 BOSSBAR 通道，先清后发才不会被自己抹掉
        messages.clearHud(player);
        if (reasonKey != null) {
            messages.send(player, reasonKey, quotaPairs(quota));
        }
        if (protectedBySlowFall) {
            messages.send(player, Msg.QUOTA_EXHAUSTED_PROTECT);
        }
    }

    /** 静默结束会话：不提示、不给缓降。用在落地和超时上。 */
    private void endSession(ServerPlayer player, PlayerQuota quota) {
        setAbilities(player, false);
        quota.setFlightGranted(false);
        quota.setFlightForced(false);
        quota.setAirborneSince(-1L);
        messages.clearHud(player);
    }

    private void grantAbilities(ServerPlayer player, PlayerQuota quota) {
        try {
            Abilities abilities = player.getAbilities();
            abilities.mayfly = true;
            abilities.flying = true;
            applyFlySpeed(abilities);
            player.onUpdateAbilities();
            if (quota != null) {
                quota.setFlightGranted(true);
                quota.setAirborneSince(-1L);
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("给 {} 授予飞行权限失败", player.getScoreboardName(), e);
        }
    }

    private void setAbilities(ServerPlayer player, boolean allowed) {
        try {
            Abilities abilities = player.getAbilities();
            abilities.mayfly = allowed;
            if (!allowed) {
                abilities.flying = false;
                restoreFlySpeed(abilities);
            }
            player.onUpdateAbilities();
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("更新 {} 的飞行权限失败", player.getScoreboardName(), e);
        }
    }

    /** flySpeed 配成 1.0（默认）时完全不动原版数值，免得跟别的插件打架。 */
    private void applyFlySpeed(Abilities abilities) {
        double multiplier = configService.get().flight.flySpeed;
        if (Math.abs(multiplier - 1.0D) < 1e-6) {
            return;
        }
        abilities.setFlyingSpeed((float) (BASE_FLY_SPEED * multiplier));
    }

    private void restoreFlySpeed(Abilities abilities) {
        double multiplier = configService.get().flight.flySpeed;
        if (Math.abs(multiplier - 1.0D) < 1e-6) {
            return;
        }
        abilities.setFlyingSpeed((float) BASE_FLY_SPEED);
    }

    /** 玩家身上已经有缓降就不重复给，免得把人家自己喝的药水时长冲掉。 */
    private boolean applySlowFalling(ServerPlayer player) {
        try {
            if (player.getEffect(MobEffects.SLOW_FALLING) != null) {
                return true;
            }
            int ticks = Math.max(1, configService.get().flight.safeLandingSeconds) * 20;
            player.addEffect(new MobEffectInstance(
                    MobEffects.SLOW_FALLING,
                    ticks,
                    0,
                    false,
                    configService.get().flight.safeLandingParticles));
            return true;
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("给 {} 施加缓降保护失败", player.getScoreboardName(), e);
            return false;
        }
    }

    public static String dimensionId(ServerPlayer player) {
        try {
            return player.level().dimension().identifier().toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String blockMessage(Block block) {
        return switch (block) {
            case GAMEMODE -> Msg.FLY_BLOCKED_GAMEMODE;
            case NO_QUOTA -> Msg.FLY_BLOCKED_NO_QUOTA;
            case PAUSED -> Msg.FLY_BLOCKED_PAUSED;
            case NO_EVENT -> Msg.FLY_BLOCKED_NO_EVENT;
            case WORLD -> Msg.FLY_BLOCKED_WORLD;
            case HEIGHT -> Msg.FLY_BLOCKED_HEIGHT;
            case NONE -> Msg.FLY_ON;
        };
    }

    /** 飞到一半被收回时该说哪句话 —— 跟「无法起飞」那套文案不是一回事。 */
    private static String revokeReason(Block block) {
        return switch (block) {
            case NO_EVENT -> Msg.EVENT_ENDED_KICK;
            case NO_QUOTA -> Msg.QUOTA_EXHAUSTED;
            case PAUSED -> Msg.FLY_BLOCKED_PAUSED;
            default -> blockMessage(block);
        };
    }

    /** 文案占位符。抽出来是因为到处都要同一套数值。 */
    public static Object[] quotaPairs(PlayerQuota quota) {
        long pausable = quota == null ? 0L : quota.getPausableMillis();
        long fixed = quota == null ? 0L : quota.getFixedMillis();
        return new Object[]{
                "pausable", Durations.format(pausable),
                "fixed", Durations.format(fixed),
                "total", Durations.format(pausable + fixed),
                "pausable_short", Durations.formatShort(pausable),
                "fixed_short", Durations.formatShort(fixed),
                "total_short", Durations.formatShort(pausable + fixed),
                "timer", quota != null && quota.isPaused() ? "已暂停" : "计时中",
        };
    }
}
