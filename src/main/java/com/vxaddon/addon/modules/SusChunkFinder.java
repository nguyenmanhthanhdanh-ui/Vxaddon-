
package com.vxaddon.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> amethyst = sgGeneral.add(
        new BoolSetting.Builder().name("amethyst").defaultValue(true).build()
    );

    private final Setting<Boolean> beeNest = sgGeneral.add(
        new BoolSetting.Builder().name("bee-nest").defaultValue(true).build()
    );

    private final Setting<Boolean> kelp = sgGeneral.add(
        new BoolSetting.Builder().name("kelp").defaultValue(true).build()
    );

    private final Setting<Boolean> vine = sgGeneral.add(
        new BoolSetting.Builder().name("vine").defaultValue(true).build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(
        new IntSetting.Builder().name("sensitivity").defaultValue(4).range(1, 20).build()
    );

    private final Setting<Integer> distance = sgGeneral.add(
        new IntSetting.Builder().name("distance").defaultValue(5).range(1, 15).build()
    );

    private final Setting<Integer> opacity = sgRender.add(
        new IntSetting.Builder().name("opacity").defaultValue(60).range(0, 100).build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder().name("color")
            .defaultValue(new SettingColor(255, 0, 0, 255)).build()
    );

    private final Map<Long, Integer> results = new HashMap<>();

    public SusChunkFinder() {
        super(com.vxaddon.addon.VxAddon.CATEGORY,
            "sus-chunk-finder",
            "Finds chunks containing selected block indicators.");
    }

    @Override
    public void onActivate() {
        results.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        int cx = mc.player.chunkPosition().x;
        int cz = mc.player.chunkPosition().z;

        for (int x = cx - distance.get(); x <= cx + distance.get(); x++) {
            for (int z = cz - distance.get(); z <= cz + distance.get(); z++) {
                long key = ChunkPos.asLong(x, z);
                if (results.containsKey(key)) continue;

                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);

                if (chunk != null) {
                    results.put(key, scanChunk(chunk));
                }
            }
        }
    }

    private int scanChunk(LevelChunk chunk) {
        int score = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = mc.level.getMinY(); y < mc.level.getMaxY(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(chunk.getPos().getMinBlockX() + x, y,
                        chunk.getPos().getMinBlockZ() + z);

                    var block = chunk.getBlockState(pos).getBlock();

                    if (amethyst.get() && block == Blocks.AMETHYST_CLUSTER) score++;
                    if (beeNest.get() && block == Blocks.BEE_NEST) score++;
                    if (kelp.get() && block == Blocks.KELP) score++;
                    if (vine.get() && block == Blocks.VINE) score++;

                    if (score >= sensitivity.get()) return score;
                }
            }
        }
        return score;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor c = color.get();
        SettingColor renderColor = new SettingColor(
            c.r, c.g, c.b, opacity.get() * 255 / 100
        );

        for (Map.Entry<Long, Integer> entry : results.entrySet()) {
            if (entry.getValue() < sensitivity.get()) continue;

            int x = ChunkPos.getX(entry.getKey());
            int z = ChunkPos.getZ(entry.getKey());

            event.renderer.box(
                x * 16.0, mc.level.getMinY(), z * 16.0,
                x * 16.0 + 16, mc.level.getMaxY(), z * 16.0 + 16,
                renderColor, renderColor, ShapeMode.Both, 2
            );
        }
    }
      }
