
package com.vxaddon.addon.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetect = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .defaultValue(5)
        .min(1)
        .max(15)
        .build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .defaultValue(4)
        .min(1)
        .max(20)
        .build()
    );

    private final Setting<Boolean> amethyst = sgDetect.add(new BoolSetting.Builder()
        .name("amethyst")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> beeNest = sgDetect.add(new BoolSetting.Builder()
        .name("bee-nest")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> kelp = sgDetect.add(new BoolSetting.Builder()
        .name("kelp")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vine = sgDetect.add(new BoolSetting.Builder()
        .name("vine")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> storage = sgDetect.add(new BoolSetting.Builder()
        .name("storage")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .defaultValue(60)
        .min(0)
        .max(255)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 25, 25, 255))
        .build()
    );

    private final java.util.Map<ChunkPos, Integer> suspiciousChunks = new java.util.HashMap<>();

    public SusChunkFinder() {
        super(com.vxaddon.addon.VxAddon.CATEGORY,
            "sus-chunk-finder",
            "Highlights loaded chunks with unusual block patterns."
        );
    }

    @Override
    public void onActivate() {
        suspiciousChunks.clear();
    }

    @Override
    public void onDeactivate() {
        suspiciousChunks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        suspiciousChunks.clear();

        ChunkPos playerChunk = mc.player.chunkPosition();

        int radius = distance.get();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int cx = playerChunk.x + x;
                int cz = playerChunk.z + z;

                LevelChunk chunk = mc.level.getChunkSource()
                    .getChunk(cx, cz, false);

                if (chunk == null) continue;

                int score = 0;

                int minY = Math.max(-60, mc.level.getMinY());
                int maxY = Math.min(319, mc.level.getMaxY() - 1);

                for (BlockPos pos : BlockPos.betweenClosed(
                    cx * 16, minY, cz * 16,
                    cx * 16 + 15, maxY, cz * 16 + 15
                )) {
                    var block = mc.level.getBlockState(pos).getBlock();

                    if (amethyst.get() &&
                        (block == Blocks.AMETHYST_CLUSTER ||
                         block == Blocks.BUDDING_AMETHYST)) score++;

                    if (beeNest.get() &&
                        (block == Blocks.BEE_NEST ||
                         block == Blocks.BEEHIVE)) score++;

                    if (kelp.get() &&
                        (block == Blocks.KELP ||
                         block == Blocks.KELP_PLANT)) score++;

                    if (vine.get() && block == Blocks.VINE) score++;

                    if (storage.get() &&
                        (block == Blocks.CHEST ||
                         block == Blocks.TRAPPED_CHEST ||
                         block == Blocks.BARREL ||
                         block instanceof net.minecraft.world.level.block.ShulkerBoxBlock))
                        score++;
                }

                if (score >= sensitivity.get()) {
                    suspiciousChunks.put(new ChunkPos(cx, cz), score);
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        for (ChunkPos pos : suspiciousChunks.keySet()) {
            double x1 = pos.getMinBlockX();
            double z1 = pos.getMinBlockZ();
            double x2 = x1 + 16;
            double z2 = z1 + 16;

            int alpha = opacity.get();

            SettingColor c = new SettingColor(
                color.get().r,
                color.get().g,
                color.get().b,
                alpha
            );

            event.renderer.box(
                x1, mc.level.getMinY(), z1,
                x2, mc.level.getMaxY(), z2,
                c, c, meteordevelopment.meteorclient.renderer.ShapeMode.Lines, 0
            );

            if (tracers.get()) {
                event.renderer.line(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    x1 + 8,
                    mc.player.getY(),
                    z1 + 8,
                    c
                );
            }
        }
    }
                        }
                         
