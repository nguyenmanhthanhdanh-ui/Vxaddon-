
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;

public class VxStorageESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStorage = settings.createGroup("Storage");
    private final SettingGroup sgRender = settings.createGroup("Render");

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
            .defaultValue(4096)
            .min(512)
            .max(8192)
            .sliderMin(512)
            .sliderMax(8192)
            .build()
    );

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

    private final Setting<Boolean> tracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(false)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Lines)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build()
    );

    private final List<BlockPos> storageBlocks = new ArrayList<>();
    private final List<BlockPos> scanResults = new ArrayList<>();

    private BlockPos scanOrigin;
    private int scanX;
    private int scanY;
    private int scanZ;

    public VxStorageESP() {
        super(
            VxAddon.CATEGORY,
            "vx-storage-esp",
            "Highlights nearby storage blocks."
        );
    }

    @Override
    public void onActivate() {
        storageBlocks.clear();
        scanResults.clear();
        resetScan();
    }

    @Override
    public void onDeactivate() {
        storageBlocks.clear();
        scanResults.clear();
        scanOrigin = null;
    }

    private void resetScan() {
        if (mc.player == null) return;

        scanOrigin = mc.player.blockPosition();
        scanX = -distance.get();
        scanY = -distance.get();
        scanZ = -distance.get();
        scanResults.clear();
    }

    private boolean isStorage(Block block) {
        if (chest.get() && block == Blocks.CHEST) return true;
        if (trappedChest.get() && block == Blocks.TRAPPED_CHEST) return true;
        if (barrel.get() && block == Blocks.BARREL) return true;
        if (enderChest.get() && block == Blocks.ENDER_CHEST) return true;

        return shulker.get() && block instanceof ShulkerBoxBlock;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        if (scanOrigin == null) {
            resetScan();
            return;
        }

        // Restart scan when player moves to another block.
        if (!mc.player.blockPosition().equals(scanOrigin)) {
            resetScan();
        }

        int checked = 0;
        int limit = scanSpeed.get();
        int radius = distance.get();

        while (checked < limit) {
            BlockPos pos = scanOrigin.offset(scanX, scanY, scanZ);

            if (mc.level.hasChunkAt(pos)) {
                Block block = mc.level.getBlockState(pos).getBlock();

                if (isStorage(block)) {
                    scanResults.add(pos.immutable());
                }
            }

            checked++;

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
                storageBlocks.clear();
                storageBlocks.addAll(scanResults);
                resetScan();
                break;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor c = color.get();

        for (BlockPos pos : storageBlocks) {
            event.renderer.box(
                pos,
                c,
                c,
                shapeMode.get(),
                0
            );

            if (tracers.get()) {
                event.renderer.line(
                    mc.player.getX(),
                    mc.player.getEyeY(),
                    mc.player.getZ(),
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    c
                );
            }
        }
    }
}
