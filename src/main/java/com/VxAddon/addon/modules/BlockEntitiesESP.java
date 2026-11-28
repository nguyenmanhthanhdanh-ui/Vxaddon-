
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

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class BlockEntitiesESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Block settings
    private final Setting<Boolean> furnace = sgGeneral.add(new BoolSetting.Builder()
        .name("furnace")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blastFurnace = sgGeneral.add(new BoolSetting.Builder()
        .name("blast-furnace")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smoker = sgGeneral.add(new BoolSetting.Builder()
        .name("smoker")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hopper = sgGeneral.add(new BoolSetting.Builder()
        .name("hopper")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dispenser = sgGeneral.add(new BoolSetting.Builder()
        .name("dispenser")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dropper = sgGeneral.add(new BoolSetting.Builder()
        .name("dropper")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> spawner = sgGeneral.add(new BoolSetting.Builder()
        .name("spawner")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> beacon = sgGeneral.add(new BoolSetting.Builder()
        .name("beacon")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chest")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> brewingStand = sgGeneral.add(new BoolSetting.Builder()
        .name("brewing-stand")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .defaultValue(16)
        .min(4)
        .max(32)
        .sliderMin(4)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> scanSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("scan-speed")
        .defaultValue(4096)
        .min(512)
        .max(8192)
        .sliderMin(512)
        .sliderMax(8192)
        .build()
    );

    // Render settings
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity")
        .defaultValue(50)
        .min(0)
        .max(255)
        .sliderMin(0)
        .sliderMax(255)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    // Cache
    private final Map<ChunkPos, Set<BlockPos>> cache = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final Queue<ScanTask> scanQueue = new ArrayDeque<>();

    private net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private ChunkPos lastPlayerChunk;
    private int lastDistance = -1;

    public BlockEntitiesESP() {
        super(VxAddon.CATEGORY, "block-entities-esp", "Highlights selected block entities.");
    }

    @Override
    public void onActivate() {
        lastLevel = mc.level;
        lastPlayerChunk = null;
        lastDistance = -1;
    }

    @Override
    public void onDeactivate() {
        // Giữ cache khi tắt module.
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // Đổi world thì xóa cache cũ.
        if (lastLevel != mc.level) {
            cache.clear();
            scannedChunks.clear();
            scanQueue.clear();

            lastLevel = mc.level;
            lastPlayerChunk = null;
        }

        ChunkPos playerChunk = new ChunkPos(mc.player.blockPosition());

        // Khi di chuyển sang chunk khác hoặc đổi Distance,
        // tìm thêm các chunk trong phạm vi.
        if (lastPlayerChunk == null
            || !lastPlayerChunk.equals(playerChunk)
            || lastDistance != distance.get()) {

            lastPlayerChunk = playerChunk;
            lastDistance = distance.get();

            queueNearbyChunks(playerChunk);
        }

        scanLoadedChunks();
    }

    private void queueNearbyChunks(ChunkPos center) {
        int range = distance.get();

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                ChunkPos chunk = new ChunkPos(
                    center.x + x,
                    center.z + z
                );

                if (scannedChunks.contains(chunk)) continue;
                if (!isChunkLoaded(chunk)) continue;

                boolean alreadyQueued = false;

                for (ScanTask task : scanQueue) {
                    if (task.chunk.equals(chunk)) {
                        alreadyQueued = true;
                        break;
                    }
                }

                if (!alreadyQueued) {
                    scanQueue.add(new ScanTask(chunk));
                }
            }
        }
    }

    private boolean isChunkLoaded(ChunkPos chunk) {
        if (mc.level == null) return false;

        BlockPos pos = new BlockPos(
            chunk.getMinBlockX(),
            mc.level.getMinY(),
            chunk.getMinBlockZ()
        );

        return mc.level.hasChunkAt(pos);
    }

    private void scanLoadedChunks() {
        int budget = scanSpeed.get();

        while (budget > 0 && !scanQueue.isEmpty()) {
            ScanTask task = scanQueue.peek();

            // Chunk chưa tải: bỏ task để lần sau tìm lại.
            if (!isChunkLoaded(task.chunk)) {
                scanQueue.poll();
                continue;
            }

            while (budget > 0 && !task.finished()) {
                BlockPos pos = task.nextPosition();

                if (pos == null) break;

                BlockState state = mc.level.getBlockState(pos);

                if (isTarget(state)) {
                    cache.computeIfAbsent(
                        task.chunk,
                        key -> new HashSet<>()
                    ).add(pos.immutable());
                }

                budget--;
            }

            if (task.finished()) {
                scannedChunks.add(task.chunk);
                scanQueue.poll();
            }
        }
    }

    private boolean isTarget(BlockState state) {
        return (furnace.get() && state.is(Blocks.FURNACE))
            || (blastFurnace.get() && state.is(Blocks.BLAST_FURNACE))
            || (smoker.get() && state.is(Blocks.SMOKER))
            || (hopper.get() && state.is(Blocks.HOPPER))
            || (dispenser.get() && state.is(Blocks.DISPENSER))
            || (dropper.get() && state.is(Blocks.DROPPER))
            || (spawner.get() && state.is(Blocks.SPAWNER))
            || (beacon.get() && state.is(Blocks.BEACON))
            || (enderChest.get() && state.is(Blocks.ENDER_CHEST))
            || (brewingStand.get() && state.is(Blocks.BREWING_STAND));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        SettingColor base = color.get();

        SettingColor side = new SettingColor(
            base.r,
            base.g,
            base.b,
            fillOpacity.get()
        );

        SettingColor line = new SettingColor(
            base.r,
            base.g,
            base.b,
            base.a
        );

        for (Map.Entry<ChunkPos, Set<BlockPos>> entry : cache.entrySet()) {
            ChunkPos chunk = entry.getKey();

            // Không render block thuộc chunk đã unload.
            if (!isChunkLoaded(chunk)) continue;

            for (BlockPos pos : entry.getValue()) {
                BlockState state = mc.level.getBlockState(pos);

                // Kiểm tra lại block và setting hiện tại.
                if (!isTarget(state)) continue;

                event.renderer.box(
                    pos,
                    side,
                    line,
                    shapeMode.get(),
                    0
                );

                if (tracers.get()) {
                    event.renderer.line(
                        RenderUtils.center.x,
                        RenderUtils.center.y,
                        RenderUtils.center.z,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        line
                    );
                }
            }
        }
    }

    private static class ScanTask {
        private final ChunkPos chunk;
        private final int minY;
        private final int maxY;
        private int index;

        private ScanTask(ChunkPos chunk) {
            this.chunk = chunk;
            this.minY = -64;
            this.maxY = 320;
        }

        private boolean finished() {
            return index >= 16 * 16 * (maxY - minY);
        }

        private BlockPos nextPosition() {
            if (finished()) return null;

            int height = maxY - minY;

            int yIndex = index % height;
            int column = index / height;

            int localX = column % 16;
            int localZ = column / 16;

            index++;

            return new BlockPos(
                chunk.getMinBlockX() + localX,
                minY + yIndex,
                chunk.getMinBlockZ() + localZ
            );
        }
    }
}
