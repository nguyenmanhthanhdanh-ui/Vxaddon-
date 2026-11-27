
package com.vxaddon.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class StorageESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("Khoang cach hien thi.")
        .defaultValue(32)
        .min(4)
        .max(64)
        .sliderMin(4)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> chest = sgGeneral.add(new BoolSetting.Builder()
        .name("chest")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> barrel = sgGeneral.add(new BoolSetting.Builder()
        .name("barrel")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shulker = sgGeneral.add(new BoolSetting.Builder()
        .name("shulker-box")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trappedChest = sgGeneral.add(new BoolSetting.Builder()
        .name("trapped-chest")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(255, 170, 0, 255))
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

    public StorageESP() {
        super(com.vxaddon.addon.VxAddon.CATEGORY, "storage-esp", "Highlights storage blocks.");
    }

    private boolean isStorage(Block block) {
        return (chest.get() && block == Blocks.CHEST)
            || (trappedChest.get() && block == Blocks.TRAPPED_CHEST)
            || (barrel.get() && block == Blocks.BARREL)
            || (shulker.get() && (
                block == Blocks.SHULKER_BOX
                || block == Blocks.WHITE_SHULKER_BOX
                || block == Blocks.ORANGE_SHULKER_BOX
                || block == Blocks.MAGENTA_SHULKER_BOX
                || block == Blocks.LIGHT_BLUE_SHULKER_BOX
                || block == Blocks.YELLOW_SHULKER_BOX
                || block == Blocks.LIME_SHULKER_BOX
                || block == Blocks.PINK_SHULKER_BOX
                || block == Blocks.GRAY_SHULKER_BOX
                || block == Blocks.LIGHT_GRAY_SHULKER_BOX
                || block == Blocks.CYAN_SHULKER_BOX
                || block == Blocks.PURPLE_SHULKER_BOX
                || block == Blocks.BLUE_SHULKER_BOX
                || block == Blocks.BROWN_SHULKER_BOX
                || block == Blocks.GREEN_SHULKER_BOX
                || block == Blocks.RED_SHULKER_BOX
                || block == Blocks.BLACK_SHULKER_BOX
            ));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int range = distance.get();
        BlockPos center = mc.player.blockPosition();
        int checked = 0;

        SettingColor c = color.get();
        SettingColor side = new SettingColor(c.r, c.g, c.b, opacity.get() * 255 / 100);

        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-range, -range, -range),
            center.offset(range, range, range)
        )) {
            if (checked >= 3000) break;
            checked++;

            Block block = mc.level.getBlockState(pos).getBlock();

            if (isStorage(block)) {
                event.renderer.box(
                    pos,
                    c,
                    side,
                    ShapeMode.Both,
                    0
                );
            }
        }
    }
          }
