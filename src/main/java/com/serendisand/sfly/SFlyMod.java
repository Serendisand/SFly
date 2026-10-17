package com.serendisand.sfly;

import com.serendisand.sfly.command.SFlyCommands;
import com.serendisand.sfly.config.ConfigService;
import com.serendisand.sfly.config.SFlyConfig;
import com.serendisand.sfly.data.DataService;
import com.serendisand.sfly.event.EventService;
import com.serendisand.sfly.flight.FlightService;
import com.serendisand.sfly.quota.QuotaService;
import com.serendisand.sfly.task.FlyTicker;
import com.serendisand.sfly.text.MessageService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public class SFlyMod implements ModInitializer {
    private static final String BANNER_LINE = "====================================================";

    private ConfigService configService;
    private DataService dataService;
    private QuotaService quotaService;
    private EventService eventService;
    private MessageService messageService;
    private FlightService flightService;
    private FlyTicker ticker;

    @Override
    public void onInitialize() {
        printBanner();

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(SFlyConstants.MOD_ID);
        configService = new ConfigService(configDir);
        configService.load();
        dataService = new DataService(configDir);
        dataService.load();

        messageService = new MessageService(configService::get);
        quotaService = new QuotaService(dataService, configService);
        eventService = new EventService(dataService, configService);
        flightService = new FlightService(configService, quotaService, eventService, messageService);
        ticker = new FlyTicker(configService, dataService, quotaService, eventService, flightService, messageService);

        SFlyCommands commands = new SFlyCommands(
                configService, dataService, quotaService, eventService, flightService, messageService);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> commands.register(dispatcher));

        registerEvents();
        printConfigSummary(configDir);
        SFlyConstants.LOGGER.info("✦ SFly 初始化完成，输入 /sf help 看看用法吧～ (｡･ω･｡)ﾉ♡");
    }

    private void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler != null && handler.player != null) {
                ticker.onJoin(server, handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler != null && handler.player != null) {
                ticker.onDisconnect(handler.player);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(ticker::onEndTick);

        // 受伤自动断飞。damageTaken == 0 说明实际没掉血（被盾挡了之类），不算
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken > 0.0F && entity instanceof ServerPlayer player) {
                ticker.onDamage(player);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void onServerStarted(MinecraftServer server) {
        try {
            long now = System.currentTimeMillis();
            int records = dataService.data().players.size();
            int events = eventService.allEvents().size();
            int recurring = eventService.allRecurring().size();
            int active = eventService.activeWindows(now).size();

            SFlyConstants.LOGGER.info(BANNER_LINE);
            SFlyConstants.LOGGER.info("✦ SFly 已就绪");
            SFlyConstants.LOGGER.info("✦ 额度档案 {} 份 ｜ 一次性活动 {} 个 ｜ 循环活动 {} 个 ｜ 进行中 {} 个",
                    records, events, recurring, active);
            if (configService.get().flight.requireActiveEvent && active == 0) {
                SFlyConstants.LOGGER.warn("✦ 当前没有进行中的活动，玩家暂时无法使用 /sf（配置里可关掉这个限制）");
            } else if (active > 0) {
                for (var window : eventService.activeWindows(now)) {
                    SFlyConstants.LOGGER.info("✦ 进行中：{}（剩余 {}）",
                            window.name(),
                            com.serendisand.sfly.text.Durations.format(window.remaining(now)));
                }
            }
            SFlyConstants.LOGGER.info(BANNER_LINE);
        } catch (Exception e) {
            SFlyConstants.LOGGER.error("打印启动摘要失败", e);
        }
    }

    private void onServerStopping(MinecraftServer server) {
        SFlyConstants.LOGGER.info("✦ SFly 正在存盘…");
        dataService.shutdown();
        SFlyConstants.LOGGER.info("✦ SFly 已安全卸载，下次见～");
    }

    private void printBanner() {
        String version = FabricLoader.getInstance()
                .getModContainer(SFlyConstants.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        SFlyConstants.LOGGER.info(BANNER_LINE);
        SFlyConstants.LOGGER.info("        ✦  S F l y  ✦   活动限时飞行额度系统");
        SFlyConstants.LOGGER.info("        v{}  ·  by Serendisand", version);
        SFlyConstants.LOGGER.info(BANNER_LINE);
    }

    private void printConfigSummary(Path configDir) {
        SFlyConfig config = configService.get();
        SFlyConstants.LOGGER.info("✦ 配置目录：{}", configDir);
        SFlyConstants.LOGGER.info("✦ 飞行门槛：{} ｜ 不可暂停额度离线{}继续流逝",
                config.flight.requireActiveEvent ? "需有活动进行中" : "有额度即可",
                config.quota.fixedDrainsOffline ? "会" : "不会");
        SFlyConstants.LOGGER.info("✦ 落地{}收回飞行 ｜ 受伤{}断飞 ｜ 飞行速度 ×{}",
                config.flight.revokeOnLanding ? "立即" : "不",
                config.flight.disableOnDamage ? "自动" : "不",
                config.flight.flySpeed);
        SFlyConstants.LOGGER.info("✦ 结算间隔 {} tick ｜ Actionbar {} ｜ BossBar {}",
                config.quota.settleIntervalTicks,
                config.display.actionbarIntervalTicks <= 0 ? "关闭" : "开",
                config.display.bossBarEnabled ? "开" : "关闭");
        if (!config.flight.blockedWorlds.isEmpty()) {
            SFlyConstants.LOGGER.info("✦ 禁飞世界：{}", String.join(", ", config.flight.blockedWorlds));
        }
        long retention = eventService.retentionMillis();
        SFlyConstants.LOGGER.info("✦ 已结束活动{} ｜ 排期时区 {}",
                retention < 0L ? "永久保留" : "保留 " + com.serendisand.sfly.text.Durations.format(retention) + " 后清理",
                com.serendisand.sfly.text.Zones.describe());
        if (config.flight.minY > -1 || config.flight.maxY > 0) {
            SFlyConstants.LOGGER.info("✦ 飞行高度限制：Y {} ~ {}",
                    config.flight.minY > -1 ? config.flight.minY : "不限",
                    config.flight.maxY > 0 ? config.flight.maxY : "不限");
        }
    }
}
