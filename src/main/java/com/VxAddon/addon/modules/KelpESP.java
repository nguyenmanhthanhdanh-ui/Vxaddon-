
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.client.multiplayer.ClientLevel;

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
            .sliderMax(1000)
            .build()
    );

    private final Setting<Integer> maxKelp = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-kelp")
            .defaultValue(1000)
            .min(1)
            .max(1000)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Integer> scanTick = sgGeneral.add(
        new IntSetting.Builder()
            .name("scan-tick")
            .description("Ticks between scan batches. Lower is faster.")
            .defaultValue(5)
            .min(1)
            .max(20)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chat-feedback")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showChunk = sgRender.add(
        new BoolSetting.Builder()
            .name("show-chunk")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Lines)
            .build()
    );

    private final Setting<Integer> opacity = sgRender.add(
        new IntSetting.Builder()
            .name("opacity")
            .description("Controls highlight transparency.")
            .defaultValue(60)
            .min(0)
            .max(255)
            .sliderMax(255)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .build()
    );

    private static final int BLOCKS_PER_BATCH = 4096;

    private final Map<ChunkPos, ChunkResult> cache = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final Set<ChunkPos> queuedChunks = new HashSet<>();
    private final Queue<ChunkPos> scanQueue = new ArrayDeque<>();

    private ChunkScan currentScan;
    private ChunkPos lastPlayerChunk;
    private ClientLevel cachedLevel;

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
        currentScan = null;
        scanQueue.clear();
        queuedChunks.clear();
        lastPlayerChunk = null;
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        currentScan = null;
        scanQueue.clear();
        queuedChunks.clear();
        lastPlayerChunk = null;
    }

    private void clearCache() {
        cache.clear();
        scannedChunks.clear();
        queuedChunks.clear();
        scanQueue.clear();

        currentScan = null;
        lastPlayerChunk = null;
        tickCounter = 0;
    }

    private void addChunk(ChunkPos chunk) {
        if (scannedChunks.contains(chunk)) return;
        if (queuedChunks.contains(chunk)) return;

        scanQueue.add(chunk);
        queuedChunks.add(chunk);
    }

    private void startNextScan() {
        if (mc.level == null) return;

        while (!scanQueue.isEmpty()) {
            ChunkPos next = scanQueue.poll();
            queuedChunks.remove(next);

            if (scannedChunks.contains(next)) continue;

            LevelChunk chunk = mc.level.getChunkSource()
                .getChunk(next.x, next.z, false);

            if (chunk == null) continue;

            currentScan = new ChunkScan(
                next,
                chunk,
                mc.level.getMinY(),
                mc.level.getMaxY()
            );

            return;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        if (cachedLevel != mc.level) {
            cachedLevel = (ClientLevel) mc.level;
            clearCache();
        }

        ChunkPos playerChunk = mc.player.chunkPosition();

        if (lastPlayerChunk == null ||
            !lastPlayerChunk.equals(playerChunk)) {

            lastPlayerChunk = playerChunk;
            addChunk(playerChunk);
        }

        tickCounter++;

        if (tickCounter < scanTick.get()) return;

        tickCounter = 0;

        if (currentScan == null) {
            startNextScan();

            if (currentScan == null) return;
        }

        int budget = BLOCKS_PER_BATCH;

        BlockPos.MutableBlockPos pos =
            new BlockPos.MutableBlockPos();

        while (budget > 0 &&
            currentScan.index < currentScan.totalBlocks) {

            int index = currentScan.index++;

            int x = index % 16;
            int z = (index / 16) % 16;
            int y = index / 256;

            pos.set(
                currentScan.chunkPos.x * 16 + x,
                currentScan.minY + y,
                currentScan.chunkPos.z * 16 + z
            );

            var state = currentScan.chunk.getBlockState(pos);

            if (state.is(Blocks.KELP) &&
                state.getValue(KelpBlock.AGE) >= 25) {

                currentScan.count++;

                if (currentScan.firstKelpPos == null) {
                    currentScan.firstKelpPos = pos.immutable();
                }
            }

            budget--;
        }

        if (currentScan.index >= currentScan.totalBlocks) {
            finishScan();
        }
    }

    private void finishScan() {
        if (currentScan == null) return;

        ChunkPos chunk = currentScan.chunkPos;
        int count = currentScan.count;

        scannedChunks.add(chunk);

        if (count >= minKelp.get() &&
            count <= maxKelp.get()) {

            ChunkResult result = new ChunkResult(
                count,
                currentScan.firstKelpPos
            );

            cache.put(chunk, result);

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
            cache.put(
                chunk,
                new ChunkResult(count, null)
            );
        }

        currentScan = null;
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

        SettingColor tracerColor = new SettingColor(
            base.r,
            base.g,
            base.b,
            255
        );

        for (Map.Entry<ChunkPos, ChunkResult> entry :
            cache.entrySet()) {

            ChunkPos chunk = entry.getKey();
            ChunkResult result = entry.getValue();

            if (result == null ||
                result.firstKelpPos == null) continue;

            if (result.count < minKelp.get() ||
                result.count > maxKelp.get()) continue;

            if (!mc.level.hasChunk(chunk.x, chunk.z)) continue;

            double x1 = chunk.getMinBlockX();
            double z1 = chunk.getMinBlockZ();

            double x2 = x1 + 16;
            double z2 = z1 + 16;

            double y = result.firstKelpPos.getY() + 0.02;

            if (showChunk.get()) {
                event.renderer.box(
                    x1, y, z1,
                    x2, y + 0.02, z2,
                    c, c,
                    shapeMode.get(),
                    0
                );
            }

            if (tracers.get()) {
                event.renderer.line(
                    RenderUtils.center.x,
                    RenderUtils.center.y,
                    RenderUtils.center.z,
                    x1 + 8,
                    y,
                    z1 + 8,
                    tracerColor
                );
            }
        }
    }

    private static class ChunkScan {
        private final ChunkPos chunkPos;
        private final LevelChunk chunk;

        private final int minY;
        private final int totalBlocks;

        private int index;
        private int count;

        private BlockPos firstKelpPos;

        private ChunkScan(
            ChunkPos chunkPos,
            LevelChunk chunk,
            int minY,
            int maxY
        ) {
            this.chunkPos = chunkPos;
            this.chunk = chunk;
            this.minY = minY;
            this.totalBlocks = 16 * 16 * (maxY - minY);
        }
    }

    private static class ChunkResult {
        private final int count;
        private final BlockPos firstKelpPos;

        private ChunkResult(int count, BlockPos firstKelpPos) {
            this.count = count;
            this.firstKelpPos = firstKelpPos;
        }
    }
    }
