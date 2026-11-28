
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class BlockEntitiesESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgRender = settings.createGroup("Render");

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

    // BLOCKS

    private final Setting<Boolean> furnace = sgBlocks.add(
        new BoolSetting.Builder()
            .name("furnace")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> blastFurnace = sgBlocks.add(
        new BoolSetting.Builder()
            .name("blast-furnace")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> smoker = sgBlocks.add(
        new BoolSetting.Builder()
            .name("smoker")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> hopper = sgBlocks.add(
        new BoolSetting.Builder()
            .name("hopper")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> dispenser = sgBlocks.add(
        new BoolSetting.Builder()
            .name("dispenser")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> dropper = sgBlocks.add(
        new BoolSetting.Builder()
            .name("dropper")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> spawner = sgBlocks.add(
        new BoolSetting.Builder()
            .name("spawner")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> beacon = sgBlocks.add(
        new BoolSetting.Builder()
            .name("beacon")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> enderChest = sgBlocks.add(
        new BoolSetting.Builder()
            .name("ender-chest")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> brewingStand = sgBlocks.add(
        new BoolSetting.Builder()
            .name("brewing-stand")
            .defaultValue(true)
            .build()
    );

    // RENDER

    private final Setting<Boolean> tracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(255, 25, 25, 255))
            .build()
    );

    // CACHE

    private final Map<ChunkPos, Set<BlockPos>> cache = new HashMap<>();

    private BlockPos scanOrigin;
    private ChunkPos lastPlayerChunk;
    private Object cachedLevel;

    private int scanX;
    private int scanY;
    private int scanZ;
    private int scanDistance;

    public BlockEntitiesESP() {
        super(
            VxAddon.CATEGORY,
            "block-entities-esp",
            "Highlights selected block entity blocks."
        );
    }

    @Override
    public void onActivate() {
        scanOrigin = null;
        lastPlayerChunk = null;
    }

    @Override
    public void onDeactivate() {
        // Keep cache during the current world session.
        scanOrigin = null;
        lastPlayerChunk = null;
    }

    private void clearCache() {
        cache.clear();

        scanOrigin = null;
        lastPlayerChunk = null;
    }

    private void startScan() {
        if (mc.player == null) return;

        scanOrigin = mc.player.blockPosition();
        lastPlayerChunk = mc.player.chunkPosition();

        scanDistance = distance.get();

        resetScanPosition();
    }

    private void resetScanPosition() {
        scanX = -scanDistance;
        scanY = -scanDistance;
        scanZ = -scanDistance;
    }

    // Detect all target blocks regardless of settings.
    private boolean isCandidate(Block block) {
        return block == Blocks.FURNACE
            || block == Blocks.BLAST_FURNACE
            || block == Blocks.SMOKER
            || block == Blocks.HOPPER
            || block == Blocks.DISPENSER
            || block == Blocks.DROPPER
            || block == Blocks.SPAWNER
            || block == Blocks.BEACON
            || block == Blocks.ENDER_CHEST
            || block == Blocks.BREWING_STAND;
    }

    // Check the setting for each block.
    private boolean isEnabledTarget(Block block) {
        if (furnace.get() && block == Blocks.FURNACE) return true;
        if (blastFurnace.get() && block == Blocks.BLAST_FURNACE) return true;
        if (smoker.get() && block == Blocks.SMOKER) return true;
        if (hopper.get() && block == Blocks.HOPPER) return true;
        if (dispenser.get() && block == Blocks.DISPENSER) return true;
        if (dropper.get() && block == Blocks.DROPPER) return true;
        if (spawner.get() && block == Blocks.SPAWNER) return true;
        if (beacon.get() && block == Blocks.BEACON) return true;
        if (enderChest.get() && block == Blocks.ENDER_CHEST) return true;
        if (brewingStand.get() && block == Blocks.BREWING_STAND) return true;

        return false;
    }

    private void scanNextBlock() {
        if (mc.level == null || scanOrigin == null) return;

        BlockPos pos = scanOrigin.offset(
            scanX,
            scanY,
            scanZ
        );

        // Do not mark unloaded positions as scanned.
        if (mc.level.hasChunkAt(pos)) {
            Block block = mc.level.getBlockState(pos).getBlock();

            if (isCandidate(block)) {
                ChunkPos chunk = new ChunkPos(pos);

                cache.computeIfAbsent(
                    chunk,
                    key -> new HashSet<>()
                ).add(pos.immutable());
            }
        }

        // Move to next position.
        scanZ++;

        if (scanZ > scanDistance) {
            scanZ = -scanDistance;
            scanY++;
        }

        if (scanY > scanDistance) {
            scanY = -scanDistance;
            scanX++;
        }

        // Restart after finishing the scan area.
        if (scanX > scanDistance) {
            resetScanPosition();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        // Clear cache when changing world.
        if (cachedLevel != mc.level) {
            cachedLevel = mc.level;
            clearCache();
        }

        ChunkPos currentChunk = mc.player.chunkPosition();

        // Start a new scan when moving to another chunk
        // or changing the scan distance.
        if (lastPlayerChunk == null
            || !lastPlayerChunk.equals(currentChunk)
            || scanDistance != distance.get()) {

            startScan();
        }

        int budget = scanSpeed.get();

        while (budget > 0) {
            scanNextBlock();
            budget--;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor c = color.get();

        double maxDistance = distance.get();
        double maxDistanceSquared = maxDistance * maxDistance;

        for (Map.Entry<ChunkPos, Set<BlockPos>> entry : cache.entrySet()) {
            ChunkPos chunk = entry.getKey();

            // Do not render unloaded chunks.
            if (!mc.level.hasChunk(chunk.x, chunk.z)) continue;

            for (BlockPos pos : entry.getValue()) {
                if (!mc.level.hasChunkAt(pos)) continue;

                double dx = pos.getX() + 0.5 - mc.player.getX();
                double dy = pos.getY() + 0.5 - mc.player.getY();
                double dz = pos.getZ() + 0.5 - mc.player.getZ();

                double distSquared =
                    dx * dx + dy * dy + dz * dz;

                if (distSquared > maxDistanceSquared) continue;

                Block block = mc.level.getBlockState(pos).getBlock();

                // Skip replaced blocks or disabled targets.
                if (!isEnabledTarget(block)) continue;

                event.renderer.box(
                    pos,
                    c,
                    c,
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
                        c
                    );
                }
            }
        }
    }
    }
            
