
package com.vxaddon.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntitiesESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .defaultValue(32)
        .min(4)
        .max(64)
        .sliderMin(4)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> chest = toggle("chest", true);
    private final Setting<Boolean> furnace = toggle("furnace", true);
    private final Setting<Boolean> hopper = toggle("hopper", true);
    private final Setting<Boolean> dispenser = toggle("dispenser", true);
    private final Setting<Boolean> spawner = toggle("spawner", true);
    private final Setting<Boolean> beacon = toggle("beacon", true);
    private final Setting<Boolean> enderChest = toggle("ender-chest", true);

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .defaultValue(50)
        .min(0)
        .max(100)
        .sliderMin(0)
        .sliderMax(100)
        .build()
    );

    public BlockEntitiesESP() {
        super(com.vxaddon.addon.VxAddon.CATEGORY, "block-entities-esp", "Highlights selected block entities.");
    }

    private Setting<Boolean> toggle(String name, boolean value) {
        return sgGeneral.add(new BoolSetting.Builder()
            .name(name)
            .defaultValue(value)
            .build()
        );
    }

    private boolean enabled(BlockEntityType<?> type) {
        if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST)
            return chest.get();
        if (type == BlockEntityType.FURNACE || type == BlockEntityType.BLAST_FURNACE
            || type == BlockEntityType.SMOKER)
            return furnace.get();
        if (type == BlockEntityType.HOPPER) return hopper.get();
        if (type == BlockEntityType.DISPENSER || type == BlockEntityType.DROPPER)
            return dispenser.get();
        if (type == BlockEntityType.MOB_SPAWNER) return spawner.get();
        if (type == BlockEntityType.BEACON) return beacon.get();
        if (type == BlockEntityType.ENDER_CHEST) return enderChest.get();
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int range = distance.get();
        BlockPos center = mc.player.blockPosition();
        SettingColor c = color.get();
        SettingColor side = new SettingColor(c.r, c.g, c.b, opacity.get() * 255 / 100);

        for (BlockEntity entity : mc.level.blockEntityList) {
            BlockPos pos = entity.getBlockPos();

            if (Math.abs(pos.getX() - center.getX()) > range
                || Math.abs(pos.getY() - center.getY()) > range
                || Math.abs(pos.getZ() - center.getZ()) > range) continue;

            if (enabled(entity.getType())) {
                event.renderer.box(pos, c, side, ShapeMode.Both, 0);
            }
        }
    }
             }
