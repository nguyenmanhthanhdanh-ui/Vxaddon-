
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.BarrelBlock;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class VxStorageESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStorage = settings.createGroup("Storage");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgOpened = settings.createGroup("Opened Rendering");

    // GENERAL

    private final Setting<Integer> distance = sgGeneral.add(
        new IntSetting.Builder()
            .name("distance")
            .defaultValue(16)
            .min(4)
            .max(32)
            .sliderMin(4)
            .sliderMax(32)
            .build()
    );

    private final Setting<Integer> scanSpeed = sgGeneral.add(
        new IntSetting.Builder()
            .name("scan-speed")
            .description("Number of blocks checked per tick.")
            .defaultValue(4096)
            .min(512)
            .max(8192)
            .sliderMin(512)
            .sliderMax(8192)
            .build()
    );

    // STORAGE SELECTION

    private final Setting<Boolean> chest = sgStorage.add(
        new BoolSetting.Builder()
            .name("chest")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> trappedChest = sgStorage.add(
        new BoolSetting.Builder()
            .name("trapped-chest")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> barrel = sgStorage.add(
        new BoolSetting.Builder()
            .name("barrel")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> shulker = sgStorage.add(
        new BoolSetting.Builder()
            .name("shulker")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> enderChest = sgStorage.add(
        new BoolSetting.Builder()
            .name("ender-chest")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> other = sgStorage.add(
        new BoolSetting.Builder()
            .name("other")
            .description("Hoppers, furnaces, dispensers and droppers.")
            .defaultValue(false)
            .build()
    );

    // RENDER

    private final Setting<Boolean> tracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(false)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<Integer> fillOpacity = sgRender.add(
        new IntSetting.Builder()
            .name("fill-opacity")
            .defaultValue(50)
            .min(0)
            .max(255)
            .sliderMax(255)
            .build()
    );

    private final Setting<Integer> fadeDistance = sgRender.add(
        new IntSetting.Builder()
            .name("fade-distance")
            .defaultValue(32)
            .min(1)
            .max(64)
            .sliderMax(64)
            .build()
    );

    // INDIVIDUAL COLORS

    private final Setting<SettingColor> chestColor = sgRender.add(
        new ColorSetting.Builder()
            .name("chest-color")
            .defaultValue(new SettingColor(255, 165, 0, 255))
            .build()
    );

    private final Setting<SettingColor> trappedColor = sgRender.add(
        new ColorSetting.Builder()
            .name("trapped-chest-color")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
    );

    private final Setting<SettingColor> barrelColor = sgRender.add(
        new ColorSetting.Builder()
            .name("barrel-color")
            .defaultValue(new SettingColor(255, 165, 0, 255))
            .build()
    );

    private final Setting<SettingColor> shulkerColor = sgRender.add(
        new ColorSetting.Builder()
            .name("shulker-color")
            .defaultValue(new SettingColor(255, 140, 0, 255))
            .build()
    );

    private final Setting<SettingColor> enderColor = sgRender.add(
        new ColorSetting.Builder()
            .name("ender-chest-color")
            .defaultValue(new SettingColor(160, 0, 255, 255))
            .build()
    );

    private final Setting<SettingColor> otherColor = sgRender.add(
        new ColorSetting.Builder()
            .name("other-color")
            .defaultValue(new SettingColor(160, 160, 160, 255))
            .build()
    );

    // OPENED RENDERING

    private final Setting<Boolean> highlightOpenedBarrels = sgOpened.add(
        new BoolSetting.Builder()
            .name("highlight-opened-barrels")
            .description("Use a separate color for opened barrels.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> openedColor = sgOpened.add(
        new ColorSetting.Builder()
            .name("opened-color")
            .defaultValue(new SettingColor(0, 255, 255, 255))
            .build()
    );

    // CACHE

    private final Map<ChunkPos, Set<BlockPos>> cache = new HashMap<>();
    private final Set<BlockPos> checkedPositions = new HashSet<>();

    private BlockPos scanOrigin;

    private ChunkPos lastPlayerChunk;

    private int scanX;
    private int scanY;
    private int scanZ;

    private boolean scanFinished;

    public VxStorageESP() {
        super(
            VxAddon.CATEGORY,
            "vx-storage-esp",
            "Highlights nearby storage blocks."
        );
    }

    @Override
    public void onActivate() {
        scanOrigin = null;
        lastPlayerChunk = null;
        scanFinished = false;
    }

    @Override
    public void onDeactivate() {
        // Giữ cache trong phiên chơi.
        scanOrigin = null;
        lastPlayerChunk = null;
        scanFinished = false;
    }

    private void startScan() {
        if (mc.player == null) return;

        scanOrigin = mc.player.blockPosition();

        scanX = -distance.get();
        scanY = -distance.get();
        scanZ = -distance.get();

        lastPlayerChunk = mc.player.chunkPosition();

        scanFinished = false;
    }

    private boolean isStorage(Block block) {
        if (chest.get() && block == Blocks.CHEST) return true;

        if (trappedChest.get() &&
            block == Blocks.TRAPPED_CHEST) return true;

        if (barrel.get() && block == Blocks.BARREL) return true;

        if (enderChest.get() &&
            block == Blocks.ENDER_CHEST) return true;

        if (shulker.get() && block instanceof ShulkerBoxBlock) {
            return true;
        }

        return other.get() &&
            (block == Blocks.HOPPER ||
             block == Blocks.FURNACE ||
             block == Blocks.BLAST_FURNACE ||
             block == Blocks.SMOKER ||
             block == Blocks.DISPENSER ||
             block == Blocks.DROPPER);
    }

    private void scanNextBlock() {
        if (mc.level == null || scanOrigin == null) return;

        int radius = distance.get();

        BlockPos pos = scanOrigin.offset(
            scanX,
            scanY,
            scanZ
        );

        if (checkedPositions.add(pos.immutable()) &&
            mc.level.hasChunkAt(pos)) {

            Block block = mc.level.getBlockState(pos).getBlock();

            if (isStorage(block)) {
                ChunkPos chunk = new ChunkPos(pos);

                cache.computeIfAbsent(
                    chunk,
                    key -> new HashSet<>()
                ).add(pos.immutable());
            }
        }

        scanZ++;

        if (scanZ > radius) {
            scanZ = -radius;
            scanY++;
        }

        if (scanY > radius) {
            scanY = -radius;
            scanX++;
        }

        if (scanX > radius) {
            scanFinished = true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        ChunkPos currentChunk = mc.player.chunkPosition();

        if (lastPlayerChunk == null ||
            !lastPlayerChunk.equals(currentChunk)) {
            startScan();
        }

        if (scanFinished) return;

        int budget = scanSpeed.get();

        while (budget > 0 && !scanFinished) {
            scanNextBlock();
            budget--;
        }
    }

    private SettingColor getBlockColor(Block block) {
        if (block == Blocks.CHEST) return chestColor.get();

        if (block == Blocks.TRAPPED_CHEST) {
            return trappedColor.get();
        }

        if (block == Blocks.BARREL) return barrelColor.get();

        if (block == Blocks.ENDER_CHEST) return enderColor.get();

        if (block instanceof ShulkerBoxBlock) {
            return shulkerColor.get();
        }

        return otherColor.get();
    }

    private boolean isOpenedBarrel(BlockState state) {
        return state.is(Blocks.BARREL) &&
            state.getValue(BarrelBlock.OPEN);
    }

    private SettingColor makeColor(
        SettingColor base,
        int alpha
    ) {
        return new SettingColor(
            base.r,
            base.g,
            base.b,
            Math.max(0, Math.min(255, alpha))
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = mc.gameRenderer
            .getMainCamera()
            .getPosition();

        double fadeLimit = fadeDistance.get();

        double fadeLimitSquared = fadeLimit * fadeLimit;

        for (Map.Entry<ChunkPos, Set<BlockPos>> entry :
            cache.entrySet()) {

            ChunkPos chunk = entry.getKey();

            if (!mc.level.hasChunk(chunk.x, chunk.z)) continue;

            for (BlockPos pos : entry.getValue()) {

                BlockState state = mc.level.getBlockState(pos);
                Block block = state.getBlock();

                // Block không còn là storage thì bỏ qua.
                if (!isStorage(block)) continue;

                double dx = pos.getX() + 0.5 - cameraPos.x;
                double dy = pos.getY() + 0.5 - cameraPos.y;
                double dz = pos.getZ() + 0.5 - cameraPos.z;

                double distSquared =
                    dx * dx + dy * dy + dz * dz;

                if (distSquared >= fadeLimitSquared) continue;

                double dist = Math.sqrt(distSquared);

                double fade = 1.0 - (dist / fadeLimit);

                SettingColor base = getBlockColor(block);

                // Màu riêng cho barrel đang mở.
                if (highlightOpenedBarrels.get() &&
                    isOpenedBarrel(state)) {
                    base = openedColor.get();
                }

                int lineAlpha = (int) (base.a * fade);
                int sideAlpha = (int) (
                    fillOpacity.get() * fade
                );

                SettingColor line = makeColor(
                    base,
                    lineAlpha
                );

                SettingColor side = makeColor(
                    base,
                    sideAlpha
                );

                event.renderer.box(
                    pos,
                    side,
                    line,
                    shapeMode.get(),
                    0
                );

                if (tracers.get()) {
                    event.renderer.line(
                        cameraPos.x,
                        cameraPos.y,
                        cameraPos.z,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        line
                    );
                }
            }
        }
    }
    }
            
