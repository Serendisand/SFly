package com.serendisand.sfly.text;

import com.serendisand.sfly.SFlyConstants;
import com.serendisand.sfly.config.SFlyConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** 文案渲染 + 按 channel 投递。所有发送都吞异常，文案问题不该影响游戏逻辑。 */
public class MessageService {
    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 50;
    private static final int TITLE_FADE_OUT = 10;

    private final Supplier<SFlyConfig> configSupplier;
    private final Map<UUID, BossBarHandle> bossBars = new ConcurrentHashMap<>();

    public MessageService(Supplier<SFlyConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public MessageSpec spec(String key) {
        SFlyConfig config = configSupplier.get();
        MessageSpec spec = config == null ? null : config.messages.get(key);
        if (spec != null) {
            // 配置里存在就用它 —— 哪怕被清空了，也说明人家想静音，不能偷偷退回默认文案
            return spec;
        }
        MessageSpec fallback = DefaultMessages.build().get(key);
        return fallback == null ? new MessageSpec(MessageChannel.NONE) : fallback;
    }

    public void send(ServerPlayer player, String key, Object... pairs) {
        if (player == null) {
            return;
        }
        try {
            MessageSpec spec = spec(key);
            if (spec.isSilent()) {
                return;
            }
            List<Component> body = render(spec.getLines(), prefixFor(spec.getChannel()), pairs);
            switch (spec.getChannel()) {
                case CHAT, BROADCAST -> body.forEach(player::sendSystemMessage);
                case ACTIONBAR -> player.sendSystemMessage(join(body, " "), true);
                case TITLE, SUBTITLE -> sendTitle(player, spec.getChannel(), body);
                case BOSSBAR -> updateBossBar(player, join(body, " "), 1.0f);
                case NONE -> {
                }
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("向玩家发送消息 {} 失败", key, e);
        }
    }

    /** BossBar 专用入口：进度要按额度算，所以单独给一个方法。 */
    public void sendBossBar(ServerPlayer player, String key, float progress, Object... pairs) {
        if (player == null) {
            return;
        }
        try {
            MessageSpec spec = spec(key);
            if (spec.isSilent()) {
                hideBossBar(player);
                return;
            }
            updateBossBar(player, join(render(spec.getLines(), prefixFor(spec.getChannel()), pairs), " "), progress);
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("更新 BossBar {} 失败", key, e);
        }
    }

    public void hideBossBar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        BossBarHandle handle = bossBars.remove(player.getUUID());
        if (handle != null) {
            try {
                handle.event.setVisible(false);
                handle.event.removeAllPlayers();
            } catch (Exception ignored) {
                // 玩家已经退了，包发不出去很正常
            }
        }
    }

    /**
     * 飞行会话结束时立刻清屏：BossBar 收回 + Actionbar 抹掉。
     *
     * <p>只该在「会话结束」那一刻调一次 —— 每个 tick 都发空 overlay 会踩到别的插件。
     */
    public void clearHud(ServerPlayer player) {
        if (player == null) {
            return;
        }
        hideBossBar(player);
        try {
            // 原版没有「清除 actionbar」的包，发一条空 overlay 就是标准做法
            player.sendSystemMessage(Component.empty(), true);
        } catch (Exception ignored) {
            // 同上，人已经走了就算了
        }
    }

    /** 命令反馈：始终走聊天栏，避免管理员看不到结果。 */
    public void send(CommandSourceStack source, String key, Object... pairs) {
        if (source == null) {
            return;
        }
        try {
            MessageSpec spec = spec(key);
            if (spec.isSilent()) {
                return;
            }
            for (Component line : render(spec.getLines(), prefixFor(MessageChannel.CHAT), pairs)) {
                source.sendSuccess(() -> line, false);
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("向命令源发送消息 {} 失败", key, e);
        }
    }

    public void sendLines(CommandSourceStack source, List<String> templates, Object... pairs) {
        if (source == null || templates == null) {
            return;
        }
        for (String template : templates) {
            Component line = Component.literal(Texts.of(template, pairs));
            source.sendSuccess(() -> line, false);
        }
    }

    public void sendLines(ServerPlayer player, List<String> templates, Object... pairs) {
        if (player == null || templates == null) {
            return;
        }
        for (String template : templates) {
            player.sendSystemMessage(Component.literal(Texts.of(template, pairs)));
        }
    }

    public void sendError(CommandSourceStack source, String message) {
        if (source != null) {
            source.sendFailure(Component.literal(Texts.color(message)));
        }
    }

    public void broadcast(MinecraftServer server, String key, Object... pairs) {
        try {
            MessageSpec spec = spec(key);
            if (spec.isSilent()) {
                return;
            }
            List<Component> body = render(spec.getLines(), prefixFor(MessageChannel.CHAT), pairs);
            for (Component line : body) {
                if (server != null) {
                    // 原版 broadcastSystemMessage 自己就会把消息写进服务器日志，
                    // 这里不能再补一刀，否则控制台里同一行会出现两次
                    server.getPlayerList().broadcastSystemMessage(line, false);
                } else {
                    SFlyConstants.LOGGER.info(line.getString());
                }
            }
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("广播消息 {} 失败", key, e);
        }
    }

    private void updateBossBar(ServerPlayer player, Component title, float progress) {
        SFlyConfig config = configSupplier.get();
        if (config == null || !config.display.bossBarEnabled) {
            hideBossBar(player);
            return;
        }
        try {
            BossBarHandle handle = bossBars.computeIfAbsent(player.getUUID(), id -> new BossBarHandle(
                    new net.minecraft.server.level.ServerBossEvent(
                            id,
                            Component.empty(),
                            parseColor(config.display.bossBarColor),
                            parseOverlay(config.display.bossBarOverlay))));
            handle.event.setName(title);
            handle.event.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));
            handle.event.addPlayer(player);
            handle.event.setVisible(true);
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("创建 BossBar 失败", e);
        }
    }

    private static BossEvent.BossBarColor parseColor(String raw) {
        if (raw != null) {
            try {
                return BossEvent.BossBarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 配置写错了就退回粉色
            }
        }
        return BossEvent.BossBarColor.PINK;
    }

    private static BossEvent.BossBarOverlay parseOverlay(String raw) {
        if (raw != null) {
            try {
                return BossEvent.BossBarOverlay.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 同上
            }
        }
        return BossEvent.BossBarOverlay.PROGRESS;
    }

    private String prefixFor(MessageChannel channel) {
        if (channel != MessageChannel.CHAT && channel != MessageChannel.BROADCAST) {
            return "";
        }
        SFlyConfig config = configSupplier.get();
        String prefix = config == null ? null : config.prefix;
        return Texts.color(prefix == null ? "" : prefix);
    }

    private List<Component> render(List<String> templates, String prefix, Object... pairs) {
        List<Component> out = new ArrayList<>(templates.size());
        for (String template : templates) {
            out.add(Component.literal(prefix + Texts.of(template, pairs)));
        }
        return out;
    }

    private static Component join(List<Component> parts, String separator) {
        var merged = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                merged.append(separator);
            }
            merged.append(parts.get(i));
        }
        return merged;
    }

    private void sendTitle(ServerPlayer player, MessageChannel channel, List<Component> body) {
        if (player.connection == null || body.isEmpty()) {
            return;
        }
        Component title = channel == MessageChannel.TITLE ? body.getFirst() : Component.empty();
        List<Component> subtitleParts = channel == MessageChannel.TITLE ? body.subList(1, body.size()) : body;
        Component subtitle = subtitleParts.isEmpty() ? Component.empty() : join(subtitleParts, " ");

        if (title.getString().isEmpty() && subtitle.getString().isEmpty()) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
        if (!title.getString().isEmpty()) {
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
        if (!subtitle.getString().isEmpty()) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static final class BossBarHandle {
        private final net.minecraft.server.level.ServerBossEvent event;

        private BossBarHandle(net.minecraft.server.level.ServerBossEvent event) {
            this.event = event;
        }
    }
}
