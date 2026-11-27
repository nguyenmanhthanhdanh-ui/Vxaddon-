
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

import java.util.ArrayList;
import java.util.List;

public class BlockEntitiesESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
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
            .defaultValue(new SettingColor(255, 25, 25, 255))
            .build()
    );

    private final List<BlockPos> foundBlocks = new ArrayList<>();

    public BlockEntitiesESP() {
        super(
            com.vxaddon.addon.VxAddon.CATEGORY,
            "block-entities-esp",
            "Highlights selected block entity blocks."
        );
    }

    @Override
    public void onActivate() {
        foundBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        foundBlocks.clear();
    }

    private boolean isTarget(Block block) {
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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            foundBlocks.clear();
            return;
        }

        foundBlocks.clear();

        BlockPos playerPos = mc.player.blockPosition();
        int radius = distance.get();

        int minY = Math.max(
            mc.level.getMinY(),
            playerPos.getY() - radius
        );

        int maxY = Math.min(
            mc.level.getMaxY() - 1,
            playerPos.getY() + radius
        );

        for (int x = playerPos.getX() - radius;
             x <= playerPos.getX() + radius; x++) {

            for (int y = minY; y <= maxY; y++) {

                for (int z = playerPos.getZ() - radius;
                     z <= playerPos.getZ() + radius; z++) {

                    BlockPos pos = new BlockPos(x, y, z);

                    Block block = mc.level.getBlockState(pos).getBlock();

                    if (isTarget(block)) {
                        foundBlocks.add(pos);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor c = color.get();

        for (BlockPos pos : foundBlocks) {
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
                    mc.player.getY() + mc.player.getEyeHeight(),
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
      
