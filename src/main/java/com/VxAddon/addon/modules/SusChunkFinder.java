
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

    private final Setting<Integer> scanSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("scan-speed")
        .defaultValue(20)
        .min(1)
        .max(1000)
        .sliderMin(1)
        .sliderMax(200)
        .build());

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .defaultValue(true)
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
    private final Queue<ScanTask> scanQueue = new ArrayDeque<>();
    private final Set<ChunkPos> queuedChunks = new HashSet<>();

    private net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private ChunkPos lastPlayerChunk;
    private int lastDistance = -1;

    public SusChunkFinder() {
        super(VxAddon.CATEGORY, "sus-chunk-finder", "Finds suspicious chunks.");
    }

    @Override
    public void onActivate() {
        cache.clear();
        scannedChunks.clear();
        scanQueue.clear();
        queuedChunks.clear();

        lastLevel = null;
        lastPlayerChunk = null;
        lastDistance = -1;
    }

    @Override
    public void onDeactivate() {
        scanQueue.clear();
        queuedChunks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (lastLevel != mc.level) {
            cache.clear();
            scannedChunks.clear();
            scanQueue.clear();
            queuedChunks.clear();

            lastLevel = mc.level;
            lastPlayerChunk = null;
            lastDistance = -1;
        }

        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());
        int currentDistance = distance.get();

        if (lastPlayerChunk == null
            || !lastPlayerChunk.equals(playerChunk)
            || lastDistance != currentDistance) {

            lastPlayerChunk = playerChunk;
            lastDistance = currentDistance;
        }

        queueNearbyChunks(playerChunk);
        scanLoadedChunks();
    }

    private void queueNearbyChunks(ChunkPos center) {
        int range = distance.get();

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                ChunkPos pos = new ChunkPos(center.x + x, center.z + z);

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
    }

    private boolean isChunkLoaded(ChunkPos pos) {
        if (mc.level == null) return false;

        return mc.level.hasChunkAt(new BlockPos(
            pos.getMinBlockX(),
            mc.level.getMinY(),
            pos.getMinBlockZ()
        ));
    }

    private void scanLoadedChunks() {
        int speed = scanSpeed.get();

        for (int i = 0; i < speed && !scanQueue.isEmpty(); i++) {
            ScanTask task = scanQueue.poll();

            if (task == null) break;

            if (!isChunkLoaded(task.chunk)) {
                scanQueue.add(task);
                continue;
            }

            int processed = 0;

            while (!task.finished() && processed < 256) {
                BlockPos pos = task.nextPosition();

                if (pos != null) {
                    checkBlock(task, pos);
                }

                processed++;
            }

            if (task.finished()) {
                Detection detection = task.finish(sensitivity.get());

                if (!detection.found.isEmpty()) {
                    cache.put(task.chunk, detection);
                    sendFeedback(detection);
                } else {
                    cache.remove(task.chunk);
                }

                scannedChunks.add(task.chunk);
                queuedChunks.remove(task.chunk);
            } else {
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

        int honeyLevel = state.is(Blocks.BEE_NEST) || state.is(Blocks.BEEHIVE)
            ? state.getValue(BeehiveBlock.HONEY_LEVEL)
            : 0;

        if (beeNest.get()
            && honeyLevel >= 1
            && honeyLevel <= 5) {
            task.add("Bee Nest", pos);
        }

        if (kelp.get() && (
            state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
        )) {
            task.add("Kelp", pos);
        }

        if (vine.get() && state.is(Blocks.VINE) && isLongVine(pos)) {
            task.add("Long Vine", pos);
        }

        if (storage.get() && isStorage(state)) {
            task.add("Storage", pos);
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

        for (Map.Entry<ChunkPos, Detection> entry : cache.entrySet()) {
            ChunkPos chunk = entry.getKey();

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
                Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

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

            int height = maxY - minY;
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

        private Detection finish(int sensitivity) {
            Detection result = new Detection();

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() >= sensitivity) {
                    BlockPos pos = positions.get(entry.getKey());

                    result.found.add(new Found(entry.getKey(), pos));
                }
            }

            return result;
        }
    }
            }
                
