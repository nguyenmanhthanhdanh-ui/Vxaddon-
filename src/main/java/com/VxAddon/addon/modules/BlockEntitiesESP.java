
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General
    private final Setting<Boolean> amethyst = sgGeneral.add(new BoolSetting.Builder()
        .name("amethyst")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> beeNest = sgGeneral.add(new BoolSetting.Builder()
        .name("bee-nest")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> kelp = sgGeneral.add(new BoolSetting.Builder()
        .name("kelp")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> vine = sgGeneral.add(new BoolSetting.Builder()
        .name("vine")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> storage = sgGeneral.add(new BoolSetting.Builder()
        .name("storage")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .defaultValue(5)
        .min(1)
        .max(15)
        .sliderMin(1)
        .sliderMax(15)
        .build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .defaultValue(4)
        .min(1)
        .max(20)
        .sliderMin(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> scanSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("scan-speed")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderMin(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .defaultValue(true)
        .build()
    );

    // Render
    private final Setting<Boolean> showChunk = sgRender.add(new BoolSetting.Builder()
        .name("show-chunk")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> showChunkHeight = sgRender.add(new IntSetting.Builder()
        .name("show-chunk-height")
        .defaultValue(64)
        .min(-64)
        .max(320)
        .sliderMin(-64)
        .sliderMax(320)
        .visible(showChunk::get)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(showChunk::get)
        .build()
    );

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .defaultValue(60)
        .min(0)
        .max(255)
        .sliderMin(0)
        .sliderMax(255)
        .visible(showChunk::get)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    // Cache
    private final Map<ChunkPos, ScanTask> scanTasks = new HashMap<>();
    private final Map<ChunkPos, Detection> cache = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();

    private net.minecraft.client.multiplayer.ClientLevel lastLevel;

    public SusChunkFinder() {
        super(VxAddon.CATEGORY, "sus-chunk-finder", "Finds suspicious chunks.");
    }

    @Override
    public void onActivate() {
        // Không xóa cache khi bật lại module.
        lastLevel = mc.level;
    }

    @Override
    public void onDeactivate() {
        // Giữ cache khi tắt module.
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // Chỉ xóa cache khi chuyển sang world khác.
        if (lastLevel != mc.level) {
            cache.clear();
            scannedChunks.clear();
            scanTasks.clear();
            lastLevel = mc.level;
        }

        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());

        // Tìm các chunk đã tải trong phạm vi Distance.
        for (int x = -distance.get(); x <= distance.get(); x++) {
            for (int z = -distance.get(); z <= distance.get(); z++) {
                if (x * x + z * z > distance.get() * distance.get()) continue;

                ChunkPos pos = new ChunkPos(
                    playerChunk.x + x,
                    playerChunk.z + z
                );

                if (scannedChunks.contains(pos) || scanTasks.containsKey(pos)) continue;
                if (!isChunkLoaded(pos)) continue;

                scanTasks.put(pos, new ScanTask(pos, mc.level.getMinY(), mc.level.getMaxY()));
            }
        }

        // Scan Speed: số block kiểm tra mỗi tick.
        int budget = scanSpeed.get() * 256;

        Iterator<Map.Entry<ChunkPos, ScanTask>> iterator =
            scanTasks.entrySet().iterator();

        while (iterator.hasNext() && budget > 0) {
            ScanTask task = iterator.next().getValue();

            if (!isChunkLoaded(task.chunkPos)) continue;

            while (budget > 0 && !task.finished()) {
                BlockPos pos = task.nextPosition();
                if (pos == null) break;

                checkBlock(task, pos);
                budget--;
            }

            if (task.finished()) {
                scannedChunks.add(task.chunkPos);

                Detection detection = task.finish(sensitivity.get());

                if (detection != null && !detection.found.isEmpty()) {
                    cache.put(task.chunkPos, detection);
                    sendFeedback(task.chunkPos, detection);
                }

                iterator.remove();
            }
        }
    }

    private boolean isChunkLoaded(ChunkPos pos) {
        if (mc.level == null) return false;

        BlockPos checkPos = new BlockPos(
            pos.getMinBlockX(),
            mc.level.getMinY(),
            pos.getMinBlockZ()
        );

        return mc.level.hasChunkAt(checkPos);
    }

    private void checkBlock(ScanTask task, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);

        // Amethyst Cluster trưởng thành.
        if (amethyst.get() && state.is(Blocks.AMETHYST_CLUSTER)) {
            task.add("Amethyst", pos);
        }

        // Bee Nest chỉ khi đầy mật.
        if (beeNest.get()
            && state.is(Blocks.BEE_NEST)
            && state.getValue(BeehiveBlock.HONEY_LEVEL) == 5) {
            task.add("Bee Nest", pos);
        }

        // Kelp trưởng thành.
        if (kelp.get()
            && state.is(Blocks.KELP)
            && state.getValue(KelpBlock.AGE) >= 25) {
            task.add("Kelp", pos);
        }

        // Vine: dây leo liên tục ít nhất 3 block theo chiều dọc.
        if (vine.get() && state.is(Blocks.VINE) && isLongVine(pos)) {
            task.add("Vine", pos);
        }

        // Storage.
        if (storage.get() && isStorage(state)) {
            task.add("Storage", pos);
        }
    }

    private boolean isLongVine(BlockPos pos) {
        int length = 1;

        for (int i = 1; i <= 2; i++) {
            if (mc.level.getBlockState(pos.above(i)).is(Blocks.VINE)) {
                length++;
            } else {
                break;
            }
        }

        if (length >= 3) return true;

        length = 1;

        for (int i = 1; i <= 2; i++) {
            if (mc.level.getBlockState(pos.below(i)).is(Blocks.VINE)) {
                length++;
            } else {
                break;
            }
        }

        return length >= 3;
    }

    private boolean isStorage(BlockState state) {
        Block block = state.getBlock();

        return block == Blocks.CHEST
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.BARREL
            || block == Blocks.ENDER_CHEST
            || block instanceof ShulkerBoxBlock;
    }

    private void sendFeedback(ChunkPos chunkPos, Detection detection) {
        if (!chatFeedback.get()) return;

        for (Found found : detection.found) {
            String message = "[VX SusChunkFinder]\n"
                + "Phát hiện: " + found.name + "\n"
                + "Tọa độ: X: " + found.pos.getX()
                + ", Y: " + found.pos.getY()
                + ", Z: " + found.pos.getZ();

            ChatUtils.sendMsg(Component.literal(message));
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        for (Map.Entry<ChunkPos, Detection> entry : cache.entrySet()) {
            ChunkPos chunk = entry.getKey();

            // Không render chunk chưa tải.
            if (!isChunkLoaded(chunk)) continue;

            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = showChunkHeight.get();

            SettingColor base = color.get();

            if (showChunk.get()) {
                SettingColor side = new SettingColor(
                    base.r, base.g, base.b, opacity.get()
                );

                SettingColor line = new SettingColor(
                    base.r, base.g, base.b, base.a
                );

                event.renderer.box(
                    x, y, z,
                    x + 16, y + 0.02, z + 16,
                    side, line, shapeMode.get(), 0
                );
            }

            if (tracers.get() && RenderUtils.center != null) {
                event.renderer.line(
                    RenderUtils.center.x,
                    RenderUtils.center.y,
                    RenderUtils.center.z,
                    x + 8,
                    y,
                    z + 8,
                    base
                );
            }
        }
    }

    private static class ScanTask {
        private final ChunkPos chunkPos;
        private final int minY;
        private final int maxY;

        private int index;

        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, BlockPos> positions = new LinkedHashMap<>();

        private ScanTask(ChunkPos chunkPos, int minY, int maxY) {
            this.chunkPos = chunkPos;
            this.minY = minY;
            this.maxY = maxY;
        }

        private boolean finished() {
            return index >= 16 * 16 * (maxY - minY);
        }

        private BlockPos nextPosition() {
            if (finished()) return null;

            int ySize = maxY - minY;

            int yIndex = index % ySize;
            int column = index / ySize;

            int localX = column % 16;
            int localZ = column / 16;

            index++;

            return new BlockPos(
                chunkPos.getMinBlockX() + localX,
                minY + yIndex,
                chunkPos.getMinBlockZ() + localZ
            );
        }

        private void add(String name, BlockPos pos) {
            counts.put(name, counts.getOrDefault(name, 0) + 1);
            positions.putIfAbsent(name, pos.immutable());
        }

        private Detection finish(int sensitivity) {
            Detection detection = new Detection();

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() >= sensitivity) {
                    detection.found.add(
                        new Found(entry.getKey(), positions.get(entry.getKey()))
                    );
                }
            }

            return detection.found.isEmpty() ? null : detection;
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
            this.pos = pos;
        }
    }
                }
