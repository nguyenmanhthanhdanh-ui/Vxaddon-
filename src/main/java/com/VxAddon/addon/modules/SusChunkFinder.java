
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> amethyst = sgGeneral.add(new BoolSetting.Builder()
        .name("amethyst").defaultValue(true).build());

    private final Setting<Boolean> beeNest = sgGeneral.add(new BoolSetting.Builder()
        .name("bee-nest").defaultValue(false).build());

    private final Setting<Boolean> kelp = sgGeneral.add(new BoolSetting.Builder()
        .name("kelp").defaultValue(false).build());

    private final Setting<Boolean> vine = sgGeneral.add(new BoolSetting.Builder()
        .name("vine").defaultValue(true).build());

    private final Setting<Boolean> storage = sgGeneral.add(new BoolSetting.Builder()
        .name("storage").defaultValue(false).build());

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance").defaultValue(8).min(1).max(32).sliderMax(32).build());

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity").defaultValue(3).min(1).max(10).sliderMax(10).build());

    private final Setting<Integer> scanSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("scan-speed").defaultValue(20).min(1).max(1000).sliderMax(200).build());

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback").defaultValue(true).build());

    private final Setting<Boolean> showChunk = sgRender.add(new BoolSetting.Builder()
        .name("show-chunk").defaultValue(true).build());

    private final Setting<Integer> showChunkHeight = sgRender.add(new IntSetting.Builder()
        .name("show-chunk-height").defaultValue(100).min(-64).max(320).sliderMax(320).build());

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity").defaultValue(35).min(0).max(255).sliderMax(255).build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 60, 60)).build());

    private final Map<ChunkPos, Detection> cache = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final Queue<ScanTask> scanQueue = new ArrayDeque<>();
    private final Set<ChunkPos> queuedChunks = new HashSet<>();

    private net.minecraft.client.world.ClientWorld lastLevel;
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
        if (mc.player == null || mc.world == null) return;

        if (lastLevel != mc.world) {
            cache.clear();
            scannedChunks.clear();
            scanQueue.clear();
            queuedChunks.clear();

            lastLevel = mc.world;
            lastPlayerChunk = null;
            lastDistance = -1;
        }

        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());
        int currentDistance = distance.get();

        if (lastPlayerChunk == null
            || !lastPlayerChunk.equals(playerChunk)
            || lastDistance != currentDistance) {

            queueNearbyChunks(playerChunk);
            lastPlayerChunk = playerChunk;
            lastDistance = currentDistance;
        }

        // Keep retrying nearby chunks that have not been scanned.
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

                scanQueue.add(new ScanTask(pos));
                queuedChunks.add(pos);
            }
        }
    }

    private boolean isChunkLoaded(ChunkPos pos) {
        return mc.world != null
            && mc.world.getChunkManager().isChunkLoaded(pos.x, pos.z);
    }

    private void scanLoadedChunks() {
        int speed = scanSpeed.get();

        for (int i = 0; i < speed && !scanQueue.isEmpty(); i++) {
            ScanTask task = scanQueue.poll();

            if (task == null) break;

            if (!isChunkLoaded(task.chunk)) {
                // Retry later instead of permanently losing this chunk.
                scanQueue.add(task);
                continue;
            }

            WorldChunk chunk = mc.world.getChunk(task.chunk.x, task.chunk.z);

            int processed = 0;
            while (!task.finished() && processed < 256) {
                BlockPos pos = task.nextPosition();
                if (pos != null) checkBlock(task, pos);
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
        BlockState state = mc.world.getBlockState(pos);

        if (amethyst.get() && (
            state.isOf(Blocks.AMETHYST_CLUSTER)
            || state.isOf(Blocks.LARGE_AMETHYST_BUD)
        )) {
            task.add("Amethyst", pos);
        }

        if (beeNest.get()
            && (state.isOf(Blocks.BEE_NEST) || state.isOf(Blocks.BEEHIVE))
            && state.get(BeehiveBlock.HONEY_LEVEL) == 5) {
            task.add("Full Bee Nest", pos);
        }

        if (kelp.get() && (
            state.isOf(Blocks.KELP)
            || state.isOf(Blocks.KELP_PLANT)
        )) {
            task.add("Kelp", pos);
        }

        if (vine.get() && state.isOf(Blocks.VINE) && isLongVine(pos)) {
            task.add("Long Vine", pos);
        }

        if (storage.get() && isStorage(state)) {
            task.add("Storage", pos);
        }
    }

    private boolean isLongVine(BlockPos pos) {
        int length = 1;

        for (int y = 1; y <= 10; y++) {
            BlockPos check = pos.down(y);

            if (!mc.world.getBlockState(check).isOf(Blocks.VINE)) break;
            length++;
        }

        return length >= 4;
    }

    private boolean isStorage(BlockState state) {
        return state.isOf(Blocks.CHEST)
            || state.isOf(Blocks.TRAPPED_CHEST)
            || state.isOf(Blocks.BARREL)
            || state.isOf(Blocks.ENDER_CHEST)
            || state.isOf(Blocks.SHULKER_BOX)
            || state.isOf(Blocks.WHITE_SHULKER_BOX)
            || state.isOf(Blocks.ORANGE_SHULKER_BOX)
            || state.isOf(Blocks.MAGENTA_SHULKER_BOX)
            || state.isOf(Blocks.LIGHT_BLUE_SHULKER_BOX)
            || state.isOf(Blocks.YELLOW_SHULKER_BOX)
            || state.isOf(Blocks.LIME_SHULKER_BOX)
            || state.isOf(Blocks.PINK_SHULKER_BOX)
            || state.isOf(Blocks.GRAY_SHULKER_BOX)
            || state.isOf(Blocks.LIGHT_GRAY_SHULKER_BOX)
            || state.isOf(Blocks.CYAN_SHULKER_BOX)
            || state.isOf(Blocks.PURPLE_SHULKER_BOX)
            || state.isOf(Blocks.BLUE_SHULKER_BOX)
            || state.isOf(Blocks.BROWN_SHULKER_BOX)
            || state.isOf(Blocks.GREEN_SHULKER_BOX)
            || state.isOf(Blocks.RED_SHULKER_BOX)
            || state.isOf(Blocks.BLACK_SHULKER_BOX);
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
        if (mc.player == null || mc.world == null) return;

        SettingColor base = color.get();

        SettingColor fill = new SettingColor(
            base.r, base.g, base.b, opacity.get()
        );

        for (Map.Entry<ChunkPos, Detection> entry : cache.entrySet()) {
            ChunkPos chunk = entry.getKey();

            if (!isChunkLoaded(chunk)) continue;

            int x = chunk.getStartX();
            int z = chunk.getStartZ();
            int y = showChunkHeight.get();

            if (showChunk.get()) {
                event.renderer.box(
                    x, y, z,
                    x + 16, y + 0.02, z + 16,
                    fill, base, shapeMode.get(), 0
                );
            }

            if (tracers.get()) {
                Vec3d camera = mc.gameRenderer.getCamera().getPos();

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
            this.pos = pos.toImmutable();
        }
    }

    private static class ScanTask {
        private final ChunkPos chunk;
        private final int minY = -64;
        private final int maxY = 320;

        private int index = 0;
        private final Detection detection = new Detection();

        private ScanTask(ChunkPos chunk) {
            this.chunk = chunk;
        }

        private boolean finished() {
            return index >= 16 * 16 * (maxY - minY);
        }

        private BlockPos nextPosition() {
            if (finished()) return null;

            int x = index % 16;
            int z = (index / 16) % 16;
            int y = minY + index / 256;

            index++;

            return new BlockPos(
                chunk.getStartX() + x,
                y,
                chunk.getStartZ() + z
            );
        }

        private void add(String name, BlockPos pos) {
            detection.found.add(new Found(name, pos));
        }

        private Detection finish(int sensitivity) {
            return detection;
        }
    }
    }
                
