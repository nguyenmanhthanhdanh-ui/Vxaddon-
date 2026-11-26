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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public class KelpESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> distance = sgGeneral.add(
        new IntSetting.Builder()
            .name("distance")
            .defaultValue(5)
            .range(1, 15)
            .build()
    );

    private final Setting<Integer> minKelp = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-kelp")
            .defaultValue(1)
            .range(1, 5000)
            .build()
    );

    private final Setting<Integer> maxKelp = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-kelp")
            .defaultValue(5000)
            .range(1, 5000)
            .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-y")
            .defaultValue(-64)
            .range(-64, 319)
            .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-y")
            .defaultValue(319)
            .range(-64, 319)
            .build()
    );

    private final Setting<Integer> opacity = sgRender.add(
        new IntSetting.Builder()
            .name("opacity")
            .defaultValue(60)
            .range(0, 100)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .build()
    );

    private final Map<Long, Integer> results = new HashMap<>();

    private int lastMinY = -64;
    private int lastMaxY = 319;

    public KelpESP() {
        super(
            com.vxaddon.addon.VxAddon.CATEGORY,
            "kelp-esp",
            "Highlights loaded chunks containing kelp."
        );
    }

    @Override
    public void onActivate() {
        results.clear();
        lastMinY = minY.get();
        lastMaxY = maxY.get();
    }

    @Override
    public void onDeactivate() {
        results.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        if (lastMinY != minY.get() || lastMaxY != maxY.get()) {
            results.clear();
            lastMinY = minY.get();
            lastMaxY = maxY.get();
        }

        int centerX = mc.player.chunkPosition().x;
        int centerZ = mc.player.chunkPosition().z;
        int range = distance.get();

        for (int x = centerX - range; x <= centerX + range; x++) {
            for (int z = centerZ - range; z <= centerZ + range; z++) {
                long key = ChunkPos.asLong(x, z);

                if (results.containsKey(key)) continue;

                LevelChunk chunk =
                    mc.level.getChunkSource().getChunkNow(x, z);

                if (chunk != null) {
                    results.put(key, scanChunk(chunk, x, z));
                }
            }
        }

        results.keySet().removeIf(key ->
            Math.abs(ChunkPos.getX(key) - centerX) > range ||
            Math.abs(ChunkPos.getZ(key) - centerZ) > range
        );
    }

    private int scanChunk(LevelChunk chunk, int chunkX, int chunkZ) {
        int count = 0;

        int lowY = Math.max(minY.get(), mc.level.getMinY());
        int highY = Math.min(maxY.get(), mc.level.getMaxY() - 1);

        if (lowY > highY) return 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = lowY; y <= highY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(chunkX * 16 + x, y, chunkZ * 16 + z);

                    var block = chunk.getBlockState(pos).getBlock();

                    if (block == Blocks.KELP ||
                        block == Blocks.KELP_PLANT) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor selected = color.get();

        SettingColor renderColor = new SettingColor(
            selected.r,
            selected.g,
            selected.b,
            opacity.get() * 255 / 100
        );

        int centerX = mc.player.chunkPosition().x;
        int centerZ = mc.player.chunkPosition().z;
        int range = distance.get();

        for (Map.Entry<Long, Integer> entry : results.entrySet()) {
            int count = entry.getValue();

            if (count < minKelp.get() ||
                count > maxKelp.get()) continue;

            int x = ChunkPos.getX(entry.getKey());
            int z = ChunkPos.getZ(entry.getKey());

            if (Math.abs(x - centerX) > range ||
                Math.abs(z - centerZ) > range) continue;

            AABB box = new AABB(
                x * 16.0,
                minY.get(),
                z * 16.0,
                x * 16.0 + 16.0,
                maxY.get() + 1.0,
                z * 16.0 + 16.0
            );

            event.renderer.box(
                box,
                renderColor,
                renderColor,
                ShapeMode.Both,
                0
            );
        }
    }
  }
  
