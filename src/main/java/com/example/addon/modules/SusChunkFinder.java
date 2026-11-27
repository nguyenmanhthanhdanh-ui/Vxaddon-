
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> amethyst = sgGeneral.add(new BoolSetting.Builder()
        .name("amethyst")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> beeNest = sgGeneral.add(new BoolSetting.Builder()
        .name("bee-nest")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> kelp = sgGeneral.add(new BoolSetting.Builder()
        .name("kelp-grows")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> vine = sgGeneral.add(new BoolSetting.Builder()
        .name("vine-grows")
        .defaultValue(true)
        .build());

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .defaultValue(4)
        .range(1, 20)
        .build());

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .defaultValue(5)
        .range(1, 15)
        .build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .defaultValue(-64)
        .range(-64, 319)
        .build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .defaultValue(319)
        .range(-64, 319)
        .build());

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .defaultValue(60)
        .range(0, 100)
        .build());

    private final Setting<Boolean> tracer = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build());

    private final Map<Long, Integer> results = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();

    private static final int CHUNKS_PER_TICK = 4;

    public SusChunkFinder() {
        super(com.vxaddon.addon.VxAddon.CATEGORY,
            "sus-chunk-finder",
            "Finds chunks containing selected block indicators.");
    }

    @Override
    public void onActivate() {
        results.clear();
        queue.clear();
        queued.clear();
    }

    @Override
    public void onDeactivate() {
        results.clear();
        queue.clear();
        queued.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        int centerX = mc.player.chunkPosition().x;
        int centerZ = mc.player.chunkPosition().z;
        int range = distance.get();

        // Find newly loaded chunks in the scan area.
        for (int x = centerX - range; x <= centerX + range; x++) {
            for (int z = centerZ - range; z <= centerZ + range; z++) {
                long key = ChunkPos.asLong(x, z);

                if (results.containsKey(key) || queued.contains(key)) continue;

                LevelChunk chunk = mc.level.getChunkSource().getChunk(
                    x, z, ChunkStatus.FULL, false
                );

                if (chunk != null) {
                    queue.addLast(key);
                    queued.add(key);
                }
            }
        }

        // Scan a few chunks per tick.
        for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            long key = queue.removeFirst();
            queued.remove(key);

            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);

            if (Math.abs(x - centerX) > range ||
                Math.abs(z - centerZ) > range) continue;

            LevelChunk chunk = mc.level.getChunkSource().getChunk(
                x, z, ChunkStatus.FULL, false
            );

            if (chunk == null) continue;

            results.put(key, scanChunk(chunk, x, z));
        }
    }

    private int scanChunk(LevelChunk chunk, int chunkX, int chunkZ) {
        int score = 0;
        int lowY = Math.max(minY.get(), mc.level.getMinY());
        int highY = Math.min(maxY.get(), mc.level.getMaxY() - 1);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = lowY; y <= highY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(chunkX * 16 + x, y, chunkZ * 16 + z);

                    var block = chunk.getBlockState(pos).getBlock();

                    if (amethyst.get() && block == Blocks.AMETHYST_CLUSTER) score++;
                    if (beeNest.get() && block == Blocks.BEE_NEST) score++;
                    if (kelp.get() && block == Blocks.KELP) score++;
                    if (vine.get() && block == Blocks.VINE) score++;

                    if (score >= sensitivity.get()) return score;
                }
            }
        }

        return score;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int centerX = mc.player.chunkPosition().x;
        int centerZ = mc.player.chunkPosition().z;
        int range = distance.get();

        SettingColor selected = color.get();
        int alpha = opacity.get() * 255 / 100;

        SettingColor renderColor = new SettingColor(
            selected.r,
            selected.g,
            selected.b,
            alpha
        );

        for (Map.Entry<Long, Integer> entry : results.entrySet()) {
            if (entry.getValue() < sensitivity.get()) continue;

            int x = ChunkPos.getX(entry.getKey());
            int z = ChunkPos.getZ(entry.getKey());

            if (Math.abs(x - centerX) > range ||
                Math.abs(z - centerZ) > range) continue;

            double x1 = x * 16.0;
            double z1 = z * 16.0;

            event.renderer.box(
                x1, minY.get(), z1,
                x1 + 16, maxY.get() + 1, z1 + 16,
                renderColor,
                renderColor,
                ShapeMode.Both,
                2
            );

            if (tracer.get()) {
                event.renderer.line(
                    mc.player.getX(),
                    mc.player.getEyeY(),
                    mc.player.getZ(),
                    x1 + 8,
                    mc.player.getY(),
                    z1 + 8,
                    renderColor
                );
            }
        }
    }
                                                          }
                                                           
