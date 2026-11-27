package com.vxaddon.addon.modules;

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
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetect = settings.createGroup("Detection");

    private final Setting<Integer> distance = sgGeneral.add(
        new IntSetting.Builder()
            .name("distance")
            .defaultValue(5)
            .min(1)
            .max(15)
            .sliderMin(1)
            .sliderMax(15)
            .build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(
        new IntSetting.Builder()
            .name("sensitivity")
            .defaultValue(4)
            .min(1)
            .max(20)
            .sliderMin(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-y")
            .defaultValue(-60)
            .min(-64)
            .max(319)
            .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-y")
            .defaultValue(319)
            .min(-60)
            .max(319)
            .build()
    );

    private final Setting<Integer> opacity = sgGeneral.add(
        new IntSetting.Builder()
            .name("opacity")
            .defaultValue(60)
            .min(0)
            .max(100)
            .sliderMin(0)
            .sliderMax(100)
            .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> amethyst = sgDetect.add(
        new BoolSetting.Builder().name("amethyst").defaultValue(true).build()
    );

    private final Setting<Boolean> beeNest = sgDetect.add(
        new BoolSetting.Builder().name("bee-nest").defaultValue(true).build()
    );

    private final Setting<Boolean> kelp = sgDetect.add(
        new BoolSetting.Builder().name("kelp").defaultValue(true).build()
    );

    private final Setting<Boolean> vine = sgDetect.add(
        new BoolSetting.Builder().name("vine").defaultValue(true).build()
    );

    private final Setting<Boolean> storage = sgDetect.add(
        new BoolSetting.Builder().name("storage").defaultValue(true).build()
    );

    private final Setting<Boolean> blockEntities = sgDetect.add(
        new BoolSetting.Builder().name("block-entities").defaultValue(true).build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
    );

    private final Map<ChunkPos, Integer> results = new HashMap<>();

    private int scanIndex;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;

    public SusChunkFinder() {
        super(
            com.vxaddon.addon.VxAddon.CATEGORY,
            "sus-chunk-finder",
            "Finds chunks with suspicious block indicators."
        );
    }

    @Override
    public void onActivate() {
        results.clear();
        scanIndex = 0;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }

    private boolean isStorage(Block block) {
        return block == Blocks.CHEST
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.BARREL
            || block instanceof ShulkerBoxBlock;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        int centerX = mc.player.chunkPosition().x;
        int centerZ = mc.player.chunkPosition().z;

        if (centerX != lastChunkX || centerZ != lastChunkZ) {
            lastChunkX = centerX;
            lastChunkZ = centerZ;
            scanIndex = 0;
        }

        int range = distance.get();
        int side = range * 2 + 1;
        int total = side * side;

        // Quet toi da 4 chunk moi tick.
        for (int i = 0; i < 4 && scanIndex < total; i++) {
            int offsetX = scanIndex % side - range;
            int offsetZ = scanIndex / side - range;
            scanIndex++;

            int chunkX = centerX + offsetX;
            int chunkZ = centerZ + offsetZ;

            LevelChunk chunk = mc.level.getChunkSource()
                .getChunk(chunkX, chunkZ, false);

            if (chunk == null) continue;

            int count = scanChunk(chunk);

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);

            if (count >= sensitivity.get()) {
                results.put(pos, count);
            } else {
                results.remove(pos);
            }
        }

        if (scanIndex >= total) scanIndex = 0;
    }

    private int scanChunk(LevelChunk chunk) {
        int count = 0;

        int low = Math.max(-64, minY.get());
        int high = Math.min(319, maxY.get());

        if (low > high) return 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = low; y <= high; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(
                        chunk.getPos().getMinBlockX() + x,
                        y,
                        chunk.getPos().getMinBlockZ() + z
                    );

                    Block block = chunk.getBlockState(pos).getBlock();

                    if (amethyst.get() && block == Blocks.AMETHYST_CLUSTER)
                        count++;

                    if (beeNest.get() && block == Blocks.BEE_NEST)
                        count++;

                    if (kelp.get() &&
                        (block == Blocks.KELP || block == Blocks.KELP_PLANT))
                        count++;

                    if (vine.get() && block == Blocks.VINE)
                        count++;

                    if (storage.get() && isStorage(block))
                        count++;
                }
            }
        }

        if (blockEntities.get()) {
            count += chunk.getBlockEntities().size();
        }

        return count;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor c = color.get();
        SettingColor side = new SettingColor(
            c.r, c.g, c.b, opacity.get() * 255 / 100
        );

        for (Map.Entry<ChunkPos, Integer> entry : results.entrySet()) {
            ChunkPos chunk = entry.getKey();

            int x1 = chunk.getMinBlockX();
            int z1 = chunk.getMinBlockZ();
            int x2 = x1 + 16;
            int z2 = z1 + 16;

            event.renderer.box(
                x1, minY.get(), z1,
                x2, maxY.get() + 1, z2,
                side, c, ShapeMode.Both, 0
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
    }
