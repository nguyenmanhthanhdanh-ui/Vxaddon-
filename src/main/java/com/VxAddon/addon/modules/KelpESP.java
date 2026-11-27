
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

public class KelpESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minKelp = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-kelp")
            .defaultValue(1)
            .min(1)
            .max(1000)
            .build()
    );

    private final Setting<Integer> maxKelp = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-kelp")
            .defaultValue(1000)
            .min(1)
            .max(1000)
            .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chat-feedback")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(false)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Lines)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .build()
    );

    private static final int SCAN_TICK = 5;
    private static final int BLOCKS_PER_TICK = 4096;

    private final Map<ChunkPos, Integer> foundChunks = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final Queue<ChunkPos> scanQueue = new ArrayDeque<>();

    private ChunkPos lastPlayerChunk;
    private ChunkScan currentScan;

    private int tickCounter;

    public KelpESP() {
        super(
            VxAddon.CATEGORY,
            "kelp-esp",
            "Highlights chunks containing mature kelp."
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
        foundChunks.clear();
        scannedChunks.clear();
        scanQueue.clear();

        currentScan = null;
        lastPlayerChunk = null;
        tickCounter = 0;
    }

    private void addChunk(ChunkPos chunk) {
        if (scannedChunks.contains(chunk)) return;

        if (scanQueue.contains(chunk)) return;

        scanQueue.add(chunk);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            resetScanner();
            return;
        }

        ChunkPos playerChunk = mc.player.chunkPosition();

        if (lastPlayerChunk == null ||
            !lastPlayerChunk.equals(playerChunk)) {

            lastPlayerChunk = playerChunk;
            addChunk(playerChunk);
        }

        tickCounter++;

        if (currentScan == null) {
            if (tickCounter < SCAN_TICK) return;

            tickCounter = 0;

            ChunkPos next = scanQueue.poll();

            if (next == null) return;

            LevelChunk chunk = mc.level.getChunkSource()
                .getChunk(next.x, next.z, false);

            if (chunk == null) return;

            scannedChunks.add(next);

            currentScan = new ChunkScan(
                next,
                mc.level.getMinY(),
                mc.level.getMaxY()
            );
        }

        int budget = BLOCKS_PER_TICK;

        BlockPos.MutableBlockPos pos =
            new BlockPos.MutableBlockPos();

        while (budget > 0 &&
            currentScan.index < currentScan.totalBlocks) {

            int index = currentScan.index++;

            int x = index % 16;
            int z = (index / 16) % 16;
            int y = index / 256;

            pos.set(
                currentScan.chunk.x * 16 + x,
                currentScan.minY + y,
                currentScan.chunk.z * 16 + z
            );

            var state = mc.level.getBlockState(pos);

            // Only count mature kelp tips, not kelp plant stems.
            if (state.is(Blocks.KELP) &&
                state.getValue(KelpBlock.AGE) >= 25) {
                currentScan.count++;
            }

            budget--;
        }

        if (currentScan.index >= currentScan.totalBlocks) {
            finishScan();
        }
    }

    private void finishScan() {
        ChunkPos chunk = currentScan.chunk;
        int count = currentScan.count;

        if (count >= minKelp.get() &&
            count <= maxKelp.get()) {

            foundChunks.put(chunk, count);

            if (chatFeedback.get() && mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal(
                        "[VX KelpESP] Kelp: " + count +
                        " | Chunk: X: " + chunk.x +
                        ", Z: " + chunk.z
                    ),
                    false
                );
            }
        } else {
            foundChunks.remove(chunk);
        }

        currentScan = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor c = color.get();

        for (ChunkPos chunk : foundChunks.keySet()) {
            double x1 = chunk.getMinBlockX();
            double z1 = chunk.getMinBlockZ();

            double x2 = x1 + 16;
            double z2 = z1 + 16;

            event.renderer.box(
                x1, mc.level.getMinY(), z1,
                x2, mc.level.getMaxY(), z2,
                c, c, shapeMode.get(), 0
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
        private final ChunkPos chunk;
        private final int minY;
        private final int totalBlocks;

        private int index;
        private int count;

        private ChunkScan(ChunkPos chunk, int minY, int maxY) {
            this.chunk = chunk;
            this.minY = minY;
            this.totalBlocks = 16 * 16 * (maxY - minY);
        }
    }
    }
