
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> amethyst = sgGeneral.add(new BoolSetting.Builder()
        .name("amethyst")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> beeNest = sgGeneral.add(new BoolSetting.Builder()
        .name("bee-nest")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> kelp = sgGeneral.add(new BoolSetting.Builder()
        .name("kelp")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> vine = sgGeneral.add(new BoolSetting.Builder()
        .name("vine")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> storage = sgGeneral.add(new BoolSetting.Builder()
        .name("storage")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> suspiciousTerrain = sgGeneral.add(new BoolSetting.Builder()
        .name("suspicious-terrain")
        .defaultValue(false)
        .build());

    private final Setting<Integer> terrainSensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("terrain-sensitivity")
        .defaultValue(12)
        .min(4)
        .max(100)
        .sliderMin(4)
        .sliderMax(50)
        .visible(suspiciousTerrain::get)
        .build());

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderMin(1)
        .sliderMax(32)
        .build());

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .defaultValue(3)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .build());

    // Số block tối đa được quét mỗi tick.
    private final Setting<Integer> scanSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("scan-speed")
        .defaultValue(24000)
        .min(1000)
        .max(100000)
        .sliderMin(1000)
        .sliderMax(50000)
        .build());

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> feedbackSound = sgGeneral.add(new BoolSetting.Builder()
        .name("feedback-sound")
        .defaultValue(true)
        .visible(chatFeedback::get)
        .build());

    private final Setting<Boolean> showChunk = sgRender.add(new BoolSetting.Builder()
        .name("show-chunk")
        .defaultValue(true)
        .build());

    private final Setting<Integer> showChunkHeight = sgRender.add(new IntSetting.Builder()
        .name("show-chunk-height")
        .defaultValue(100)
        .min(-64)
        .max(320)
        .sliderMin(-64)
        .sliderMax(320)
        .visible(showChunk::get)
        .build());

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(showChunk::get)
        .build());

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity")
        .defaultValue(35)
        .min(0)
        .max(255)
        .sliderMin(0)
        .sliderMax(255)
        .visible(showChunk::get)
        .build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 60, 60))
        .build());

    private final Map<ChunkPos, Detection> cache = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();

    // Dùng List để có thể sắp xếp chunk theo khoảng cách.
    private final List<ScanTask> scanQueue = new ArrayList<>();
    private final Set<ChunkPos> queuedChunks = new HashSet<>();

    private net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private int lastSettingsHash = Integer.MIN_VALUE;

    public SusChunkFinder() {
        super(VxAddon.CATEGORY, "sus-chunk-finder", "Finds suspicious chunks.");
    }

    @Override
    public void onActivate() {
        clearAllData();
        lastLevel = null;
        lastSettingsHash = Integer.MIN_VALUE;
    }

    @Override
    public void onDeactivate() {
        clearAllData();
    }

    private void clearAllData() {
        cache.clear();
        scannedChunks.clear();
        scanQueue.clear();
        queuedChunks.clear();
    }

    private int getScanSettingsHash() {
        return Objects.hash(
            amethyst.get(),
            beeNest.get(),
            kelp.get(),
            vine.get(),
            storage.get(),
            suspiciousTerrain.get(),
            terrainSensitivity.get(),
            sensitivity.get()
        );
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (lastLevel != mc.level) {
            clearAllData();
            lastLevel = mc.level;
            lastSettingsHash = Integer.MIN_VALUE;
        }

        int settingsHash = getScanSettingsHash();

        if (settingsHash != lastSettingsHash) {
            clearAllData();
            lastSettingsHash = settingsHash;
        }

        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());

        removeChunksOutsideRange(playerChunk);
        removeUnloadedChunks();
        queueNearbyChunks(playerChunk);
        scanLoadedChunks(playerChunk);
    }

    private boolean isWithinRange(ChunkPos chunk, ChunkPos center) {
        int range = distance.get();

        return Math.abs(chunk.x - center.x) <= range
            && Math.abs(chunk.z - center.z) <= range;
    }

    private long getChunkDistanceSquared(ChunkPos a, ChunkPos b) {
        long dx = (long) a.x - b.x;
        long dz = (long) a.z - b.z;

        return dx * dx + dz * dz;
    }

    private void removeChunksOutsideRange(ChunkPos center) {
        cache.keySet().removeIf(chunk -> !isWithinRange(chunk, center));
        scannedChunks.removeIf(chunk -> !isWithinRange(chunk, center));

        scanQueue.removeIf(task -> !isWithinRange(task.chunk, center));

        queuedChunks.clear();

        for (ScanTask task : scanQueue) {
            queuedChunks.add(task.chunk);
        }
    }

    // Xóa dữ liệu của chunk Minecraft đã unload.
    private void removeUnloadedChunks() {
        cache.keySet().removeIf(chunk -> !isChunkLoaded(chunk));
        scannedChunks.removeIf(chunk -> !isChunkLoaded(chunk));

        scanQueue.removeIf(task -> !isChunkLoaded(task.chunk));

        queuedChunks.clear();

        for (ScanTask task : scanQueue) {
            queuedChunks.add(task.chunk);
        }
    }

    private void queueNearbyChunks(ChunkPos center) {
        int range = distance.get();

        List<ChunkPos> candidates = new ArrayList<>();

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                ChunkPos pos = new ChunkPos(center.x + x, center.z + z);

                if (scannedChunks.contains(pos)) continue;
                if (queuedChunks.contains(pos)) continue;
                if (!isChunkLoaded(pos)) continue;

                candidates.add(pos);
            }
        }

        // Chunk gần người chơi nhất được đưa vào hàng đợi trước.
        candidates.sort(Comparator.comparingLong(
            pos -> getChunkDistanceSquared(pos, center)
        ));

        for (ChunkPos pos : candidates) {
            if (scannedChunks.contains(pos)) continue;
            if (queuedChunks.contains(pos)) continue;

            scanQueue.add(new ScanTask(
                pos,
                mc.level.getMinY(),
                mc.level.getMaxY()
            ));

            queuedChunks.add(pos);
        }
    }

    private boolean isChunkLoaded(ChunkPos pos) {
        if (mc.level == null) return false;

        return mc.level.hasChunkAt(new BlockPos(
            pos.getMinBlockX(),
            mc.level.getMinY(),
            pos.getMinBlockZ()
        ));
    }

    private void scanLoadedChunks(ChunkPos playerChunk) {
        int budget = scanSpeed.get();

        // Mỗi tick sắp xếp lại để chunk gần nhất được ưu tiên.
        scanQueue.sort(Comparator.comparingLong(
            task -> getChunkDistanceSquared(task.chunk, playerChunk)
        ));

        while (budget > 0 && !scanQueue.isEmpty()) {
            ScanTask task = scanQueue.remove(0);

            if (!isChunkLoaded(task.chunk)) {
                queuedChunks.remove(task.chunk);
                cache.remove(task.chunk);
                scannedChunks.remove(task.chunk);
                continue;
            }

            int processed = 0;

            while (!task.finished() && processed < budget) {
                BlockPos pos = task.nextPosition();

                if (pos != null) {
                    checkBlock(task, pos);
                }

                processed++;
            }

            budget -= processed;

            if (task.finished()) {
                Detection detection = task.finish(
                    sensitivity.get(),
                    terrainSensitivity.get()
                );

                if (!detection.found.isEmpty()) {
                    cache.put(task.chunk, detection);
                    sendFeedback(detection);
                } else {
                    cache.remove(task.chunk);
                }

                scannedChunks.add(task.chunk);
                queuedChunks.remove(task.chunk);
            } else {
                // Tiếp tục chunk đang quét ở tick tiếp theo.
                scanQueue.add(task);
            }
        }
    }

    private void checkBlock(ScanTask task, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);

        if (amethyst.get() && (
            state.is(Blocks.AMETHYST_CLUSTER)
                || state.is(Blocks.LARGE_AMETHYST_BUD)
        )) {
            task.add("Amethyst", pos);
        }

        if (beeNest.get()
            && (state.is(Blocks.BEE_NEST) || state.is(Blocks.BEEHIVE))) {

            int honeyLevel = state.getValue(BeehiveBlock.HONEY_LEVEL);

            if (honeyLevel >= 1 && honeyLevel <= 5) {
                task.add("Bee Nest", pos);
            }
        }

        if (kelp.get() && (
            state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
        )) {
            task.add("Kelp", pos);
        }

        if (vine.get()
            && state.is(Blocks.VINE)
            && isLongVine(pos)) {

            task.add("Long Vine", pos);
        }

        if (storage.get() && isStorage(state)) {
            task.add("Storage", pos);
        }

        if (suspiciousTerrain.get() && isSuspiciousTerrain(state)) {
            task.add("Suspicious Terrain", pos);
        }
    }

    private boolean isLongVine(BlockPos pos) {
        int length = 1;

        for (int y = 1; y <= 10; y++) {
            BlockPos check = pos.below(y);

            if (!mc.level.getBlockState(check).is(Blocks.VINE)) {
                break;
            }

            length++;
        }

        return length >= 4;
    }

    private boolean isStorage(BlockState state) {
        Block block = state.getBlock();

        return state.is(Blocks.CHEST)
            || state.is(Blocks.TRAPPED_CHEST)
            || state.is(Blocks.BARREL)
            || state.is(Blocks.ENDER_CHEST)
            || block instanceof ShulkerBoxBlock;
    }

    private boolean isSuspiciousTerrain(BlockState state) {
        return state.is(Blocks.COBBLESTONE)
            || state.is(Blocks.MOSSY_COBBLESTONE)
            || state.is(Blocks.STONE_BRICKS)
            || state.is(Blocks.CRACKED_STONE_BRICKS)
            || state.is(Blocks.MOSSY_STONE_BRICKS)
            || state.is(Blocks.OAK_PLANKS)
            || state.is(Blocks.SPRUCE_PLANKS)
            || state.is(Blocks.BIRCH_PLANKS)
            || state.is(Blocks.JUNGLE_PLANKS)
            || state.is(Blocks.ACACIA_PLANKS)
            || state.is(Blocks.DARK_OAK_PLANKS)
            || state.is(Blocks.MANGROVE_PLANKS)
            || state.is(Blocks.CHERRY_PLANKS)
            || state.is(Blocks.BAMBOO_PLANKS)
            || state.is(Blocks.GLASS)
            || state.is(Blocks.TORCH)
            || state.is(Blocks.LANTERN)
            || state.is(Blocks.LADDER)
            || state.is(Blocks.IRON_BARS)
            || state.is(Blocks.RAIL)
            || state.is(Blocks.POWERED_RAIL);
    }

    private void sendFeedback(Detection detection) {
        if (!chatFeedback.get()) return;

        for (Found found : detection.found) {
            info("%s: X:%d Y:%d Z:%d",
                found.name,
                found.pos.getX(),
                found.pos.getY(),
                found.pos.getZ()
            );
        }

        if (feedbackSound.get() && mc.player != null) {
            mc.player.playSound(
                SoundEvents.NOTE_BLOCK_PLING.value(),
                0.8f,
                1.4f
            );
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        SettingColor base = color.get();

        SettingColor fill = new SettingColor(
            base.r,
            base.g,
            base.b,
            opacity.get()
        );

        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

        for (Map.Entry<ChunkPos, Detection> entry : cache.entrySet()) {
            ChunkPos chunk = entry.getKey();

            if (!isWithinRange(chunk, playerChunk)) continue;
            if (!isChunkLoaded(chunk)) continue;

            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = showChunkHeight.get();

            if (showChunk.get()) {
                event.renderer.box(
                    x, y, z,
                    x + 16, y + 0.02, z + 16,
                    fill, base, shapeMode.get(), 0
                );
            }

            if (tracers.get()) {
                event.renderer.line(
                    camera.x, camera.y, camera.z,
                    x + 8, y, z + 8,
                    base
                );
            }
        }
    }

    private static class Detection {
        private final List<Found> found = new ArrayList<>();
    }

    private static class Found {
        private final String name;
        private final BlockPos pos;

        private Found(String name, BlockPos pos) {
            this.name = name;
            this.pos = pos.immutable();
        }
    }

    private static class ScanTask {
        private final ChunkPos chunk;
        private final int minY;
        private final int maxY;

        private int index = 0;

        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, BlockPos> positions = new LinkedHashMap<>();

        private ScanTask(ChunkPos chunk, int minY, int maxY) {
            this.chunk = chunk;
            this.minY = minY;
            this.maxY = maxY;
        }

        private boolean finished() {
            return index >= 16 * 16 * (maxY - minY);
        }

        private BlockPos nextPosition() {
            if (finished()) return null;

            int yIndex = index / 256;
            int column = index % 256;

            int x = column % 16;
            int z = column / 16;
            int y = minY + yIndex;

            index++;

            return new BlockPos(
                chunk.getMinBlockX() + x,
                y,
                chunk.getMinBlockZ() + z
            );
        }

        private void add(String name, BlockPos pos) {
            counts.put(name, counts.getOrDefault(name, 0) + 1);
            positions.putIfAbsent(name, pos.immutable());
        }

        private Detection finish(int sensitivity, int terrainSensitivity) {
            Detection result = new Detection();

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                int required = entry.getKey().equals("Suspicious Terrain")
                    ? terrainSensitivity
                    : sensitivity;

                if (entry.getValue() >= required) {
                    BlockPos pos = positions.get(entry.getKey());
                    result.found.add(new Found(entry.getKey(), pos));
                }
            }

            return result;
        }
    }
    }
            
