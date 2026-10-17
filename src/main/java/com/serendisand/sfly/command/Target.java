package com.serendisand.sfly.command;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 一个命令目标。{@code player} 为 null 表示离线 —— 离线玩家照样能发额度，
 * 只是没法给他发消息、也没法立刻改他的飞行状态。
 */
public record Target(UUID id, String name, ServerPlayer player) {
    public static Target of(ServerPlayer player) {
        return new Target(player.getUUID(), player.getScoreboardName(), player);
    }

    public boolean isOnline() {
        return player != null;
    }

    public String displayName() {
        return name == null || name.isEmpty() ? id.toString() : name;
    }
}
