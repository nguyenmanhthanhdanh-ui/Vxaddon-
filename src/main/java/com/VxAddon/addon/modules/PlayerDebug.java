
package com.vxaddon.addon.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PlayerDebug extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInfo = settings.createGroup("Player Info");

    private final Setting<Double> distance = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("distance")
            .defaultValue(64)
            .min(8)
            .max(256)
            .build()
    );

    private final Setting<Boolean> showHealth = sgInfo.add(
        new BoolSetting.Builder()
            .name("health")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showDistance = sgInfo.add(
        new BoolSetting.Builder()
            .name("distance")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showCoords = sgInfo.add(
        new BoolSetting.Builder()
            .name("coordinates")
            .defaultValue(false)
            .build()
    );

    private final Setting<SettingColor> textColor = sgInfo.add(
        new ColorSetting.Builder()
            .name("text-color")
            .defaultValue(new SettingColor(255, 255, 255))
            .build()
    );

    private final List<Player> players = new ArrayList<>();
    private final Vec3 renderPos = new Vec3(0, 0, 0);

    public PlayerDebug() {
        super(
            com.vxaddon.addon.VxAddon.CATEGORY,
            "player-debug",
            "Displays information about nearby players."
        );
    }

    @Override
    public void onDeactivate() {
        players.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        players.clear();

        if (mc.level == null || mc.player == null) return;

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.isRemoved()) continue;

            if (mc.player.distanceTo(player) > distance.get()) continue;

            players.add(player);
        }
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        TextRenderer text = TextRenderer.get();

        for (Player player : players) {
            Vec3 pos = Utils.getPlayerPos(player, event.tickDelta);

            pos = pos.add(
                0,
                player.getBbHeight() + 0.5,
                0
            );

            if (!NametagUtils.to2D(pos, 1.0)) continue;

            NametagUtils.begin(pos, event.graphics);

            StringBuilder info = new StringBuilder();
            info.append(player.getName().getString());

            if (showHealth.get()) {
                info.append(" | HP: ")
                    .append(Math.round(player.getHealth()));
            }

            if (showDistance.get()) {
                info.append(" | ")
                    .append(String.format(
                        "%.1fm",
                        mc.player.distanceTo(player)
                    ));
            }

            if (showCoords.get()) {
                info.append(" | ")
                    .append(player.getBlockX())
                    .append(", ")
                    .append(player.getBlockY())
                    .append(", ")
                    .append(player.getBlockZ());
            }

            text.beginBig(event.graphics);

            text.render(
                info.toString(),
                -text.getWidth(info.toString(), true) / 2,
                0,
                textColor.get(),
                true
            );

            text.end();
            NametagUtils.end(event.graphics);
        }
    }
  }
  
