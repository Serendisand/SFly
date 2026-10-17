package com.serendisand.sfly.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 把命令里写的目标解析成人。
 *
 * <p>支持 {@code @a} / {@code @p} / {@code @s} / {@code @r}、在线玩家名、离线玩家名、UUID。
 * 没用原版 {@code EntityArgument} 是因为它只认在线玩家，而管理员经常要给还没上线的人预发额度。
 */
public final class TargetResolver {
    private TargetResolver() {
    }

    public static List<Target> resolve(CommandSourceStack source, String raw) {
        List<Target> result = new ArrayList<>();
        if (source == null || raw == null || raw.isBlank()) {
            return result;
        }
        MinecraftServer server = source.getServer();
        if (server == null) {
            return result;
        }
        String needle = raw.trim();

        if (needle.startsWith("@")) {
            resolveSelector(source, server, needle.toLowerCase(Locale.ROOT), result);
            return result;
        }

        // 在线优先 —— 同名时在线的那个才是本人
        ServerPlayer online = server.getPlayerList().getPlayerByName(needle);
        if (online != null) {
            result.add(Target.of(online));
            return result;
        }

        UUID parsedId = tryUuid(needle);
        if (parsedId != null) {
            result.add(new Target(parsedId, lookupName(server, parsedId), null));
            return result;
        }

        Optional<NameAndId> cached = server.services().nameToIdCache().get(needle);
        cached.ifPresent(entry -> result.add(new Target(entry.id(), entry.name(), null)));
        return result;
    }

    private static void resolveSelector(CommandSourceStack source, MinecraftServer server,
                                        String selector, List<Target> result) {
        var players = server.getPlayerList().getPlayers();
        switch (selector) {
            case "@a" -> players.forEach(player -> result.add(Target.of(player)));
            case "@s" -> {
                ServerPlayer self = source.getPlayer();
                if (self != null) {
                    result.add(Target.of(self));
                }
            }
            case "@p" -> {
                ServerPlayer nearest = nearest(source, players);
                if (nearest != null) {
                    result.add(Target.of(nearest));
                }
            }
            case "@r" -> {
                if (!players.isEmpty()) {
                    result.add(Target.of(players.get(ThreadLocalRandom.current().nextInt(players.size()))));
                }
            }
            // 其余选择器（@e[...] 之类）对玩家额度没意义，直接不匹配
            default -> {
            }
        }
    }

    private static ServerPlayer nearest(CommandSourceStack source, List<ServerPlayer> players) {
        ServerPlayer self = source.getPlayer();
        if (self != null) {
            return self;
        }
        if (players.isEmpty()) {
            return null;
        }
        // 命令方块/控制台执行时按距离挑最近的那个
        Vec3 origin = source.getPosition();
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double distance = player.position().distanceToSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static UUID tryUuid(String raw) {
        // 补上没写横杠的写法
        String candidate = raw.length() == 32
                ? raw.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")
                : raw;
        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String lookupName(MinecraftServer server, UUID id) {
        try {
            return server.services().nameToIdCache().get(id).map(NameAndId::name).orElse(id.toString());
        } catch (Exception e) {
            return id.toString();
        }
    }
}
