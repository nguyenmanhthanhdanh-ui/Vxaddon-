
package com.vxaddon.addon.modules;

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
            .sliderMax(32)
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

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(255, 140, 0, 255))
            .build()
    );

    private final List<BlockPos> storageBlocks = new ArrayList<>();

    public StorageESP() {
        super(
            com.vxaddon.addon.VxAddon.CATEGORY,
            "storage-esp",
            "Highlights nearby storage blocks."
        );
    }

    @Override
    public void onActivate() {
        storageBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        storageBlocks.clear();
    }

    private boolean isStorage(Block block) {
        if (chest.get() && block == Blocks.CHEST) return true;
        if (trappedChest.get() && block == Blocks.TRAPPED_CHEST) return true;
        if (barrel.get() && block == Blocks.BARREL) return true;

        return shulker.get() && block instanceof ShulkerBoxBlock;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            storageBlocks.clear();
            return;
        }

        storageBlocks.clear();

        BlockPos playerPos = mc.player.blockPosition();
        int radius = distance.get();

        for (int x = playerPos.getX() - radius;
             x <= playerPos.getX() + radius; x++) {

            for (int y = playerPos.getY() - radius;
                 y <= playerPos.getY() + radius; y++) {

                if (y < mc.level.getMinY() || y >= mc.level.getMaxY()) continue;

                for (int z = playerPos.getZ() - radius;
                     z <= playerPos.getZ() + radius; z++) {

                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = mc.level.getBlockState(pos).getBlock();

                    if (isStorage(block)) {
                        storageBlocks.add(pos);
                    }
                }
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
      
