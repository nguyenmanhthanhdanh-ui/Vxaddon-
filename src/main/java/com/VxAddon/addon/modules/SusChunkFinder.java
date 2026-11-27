
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.*;

public class SusChunkFinder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetect = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> distance = sgGeneral.add(
        new IntSetting.Builder()
            .name("distance")
            .defaultValue(5)
            .min(1)
            .max(15)
            .build()
    );

    private final Setting<Integer> scanSpeed = sgGeneral.add(
        new IntSetting.Builder()
            .name("scan-speed")
            .description("Ticks between scan batches. Lower is faster.")
            .defaultValue(5)
            .min(1)
            .max(20)
            .build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(
        new IntSetting.Builder()
            .name("sensitivity")
            .defaultValue(4)
            .min(1)
            .max(20)
            .build()
    );

    private final Setting<Boolean> amethyst = sgDetect.add(
        new BoolSetting.Builder()
            .name("amethyst")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> beeNest = sgDetect.add(
        new BoolSetting.Builder()
            .name("bee-nest")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> kelp = sgDetect.add(
        new BoolSetting.Builder()
            .name("kelp")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> vine = sgDetect.add(
        new BoolSetting.Builder()
            .name("vine")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> storage = sgDetect.add(
        new BoolSetting.Builder()
            .name("storage")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> opacity = sgRender.add(
        new IntSetting.Builder()
            .name("opacity")
            .defaultValue(60)
            .min(0)
            .max(255)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(255, 25, 25, 255))
            .build()
    );

    private static final int BLOCKS_PER_BATCH = 4096;

    private final Map<ChunkPos, Integer> suspiciousChunks = new HashMap<>();
    private final Queue<ChunkScan> scanQueue = new ArrayDeque<>();

    private ChunkScan currentScan;
    private ChunkPos lastPlayerChunk;

    private int tickCounter;

    private int minScanY;
    private int maxScanY;

    public SusChunkFinder() {
        super(
            VxAddon.CATEGORY,
            "sus-chunk-finder",
            "Highlights loaded chunks with unusual block patterns."
        );
    }

    @Override
    public void onActivate() {
        resetScanner();
    }

    @Override
    public void onDeactivate() {
        resetScanner();
    }

    private void resetScanner() {
        suspiciousChunks.clear();
        scanQueue.clear();

        currentScan = null;
        lastPlayerChunk = null;
        tickCounter = 0;
    }

    private void buildQueue() {
        scanQueue.clear();
        currentScan = null;

        if (mc.player == null || mc.level == null) return;

        ChunkPos center = mc.player.chunkPosition();
        lastPlayerChunk = center;

        int radius = distance.get();

        minScanY = Math.max(-60, mc.level.getMinY());
        maxScanY = Math.min(319, mc.level.getMaxY() - 1);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                scanQueue.add(
                    new ChunkScan(center.x + x, center.z + z)
                );
            }
        }
    }

    private boolean isTargetBlock(Block block) {
        if (amethyst.get() &&
            (block == Blocks.AMETHYST_CLUSTER ||
             block == Blocks.BUDDING_AMETHYST)) return true;

        if (beeNest.get() &&
            (block == Blocks.BEE_NEST ||
             block == Blocks.BEEHIVE)) return true;

        if (kelp.get() &&
            (block == Blocks.KELP ||
             block == Blocks.KELP_PLANT)) return true;

        if (vine.get() && block == Blocks.VINE) return true;

        return storage.get() &&
            (block == Blocks.CHEST ||
             block == Blocks.TRAPPED_CHEST ||
             block == Blocks.BARREL ||
             block instanceof ShulkerBoxBlock);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) {
            resetScanner();
            return;
        }

        ChunkPos playerChunk = mc.player.chunkPosition();

        if (lastPlayerChunk == null ||
            !lastPlayerChunk.equals(playerChunk)) {

            suspiciousChunks.clear();
            buildQueue();
        }

        tickCounter++;

        if (tickCounter < scanSpeed.get()) return;

        tickCounter = 0;

        int budget = BLOCKS_PER_BATCH;

        while (budget > 0) {

            if (currentScan == null) {
                currentScan = scanQueue.poll();

                if (currentScan == null) {
                    buildQueue();
                    return;
                }

                LevelChunk chunk = mc.level.getChunkSource()
                    .getChunk(
                        currentScan.x,
                        currentScan.z,
                        false
                    );

                if (chunk == null) {
                    currentScan = null;
                    continue;
                }
            }

            int totalBlocks = 16 * 16 * (maxScanY - minScanY + 1);

            BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos();

            while (budget > 0 &&
                   currentScan.index < totalBlocks) {

                int index = currentScan.index++;

                int x = index % 16;
                int z = (index / 16) % 16;
                int y = index / 256;

                pos.set(
                    currentScan.x * 16 + x,
                    minScanY + y,
                    currentScan.z * 16 + z
                );

                Block block = mc.level.getBlockState(pos).getBlock();

                if (isTargetBlock(block)) {
                    currentScan.score++;
                }

                budget--;
            }

            if (currentScan.index >= totalBlocks) {

                ChunkPos finished = new ChunkPos(
                    currentScan.x,
                    currentScan.z
                );

                if (currentScan.score >= sensitivity.get()) {
                    suspiciousChunks.put(
                        finished,
                        currentScan.score
                    );
                } else {
                    suspiciousChunks.remove(finished);
                }

                currentScan = null;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor base = color.get();

        SettingColor c = new SettingColor(
            base.r,
            base.g,
            base.b,
            opacity.get()
        );

        for (ChunkPos pos : suspiciousChunks.keySet()) {

            double x1 = pos.getMinBlockX();
            double z1 = pos.getMinBlockZ();

            double x2 = x1 + 16;
            double z2 = z1 + 16;

            event.renderer.box(
                x1, mc.level.getMinY(), z1,
                x2, mc.level.getMaxY(), z2,
                c, c, ShapeMode.Lines, 0
            );

            if (tracers.get()) {
                event.renderer.line(
                    mc.player.getX(),
                    mc.player.getEyeY(),
                    mc.player.getZ(),
                    x1 + 8,
                    mc.player.getY(),
                    z1 + 8,
                    c
                );
            }
        }
    }

    private static class ChunkScan {
        private final int x;
        private final int z;

        private int index;
        private int score;

        private ChunkScan(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }
}
