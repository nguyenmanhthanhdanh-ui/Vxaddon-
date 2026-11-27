
package com.vxaddon.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class KelpESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("Khoang cach quet kelp.")
        .defaultValue(32)
        .min(4)
        .max(64)
        .sliderMin(4)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Hien duong noi den kelp.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> outline = sgGeneral.add(new BoolSetting.Builder()
        .name("outline")
        .description("Hien vien khoi.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fill = sgGeneral.add(new BoolSetting.Builder()
        .name("fill")
        .description("To mau khoi.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .description("Do mo cua mau nen.")
        .defaultValue(50)
        .min(0)
        .max(100)
        .sliderMin(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Mau ESP kelp.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    public KelpESP() {
        super(com.vxaddon.addon.VxAddon.CATEGORY, "kelp-esp", "Highlights loaded kelp blocks.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        int range = distance.get();
        BlockPos center = mc.player.blockPosition();

        int checked = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-range, -range, -range),
            center.offset(range, range, range)
        )) {
            if (checked >= 2000) break;
            checked++;

            if (mc.level.getBlockState(pos).is(Blocks.KELP)
                || mc.level.getBlockState(pos).is(Blocks.KELP_PLANT)) {

                SettingColor c = color.get();
                SettingColor side = new SettingColor(c.r, c.g, c.b, opacity.get() * 255 / 100);

                ShapeMode mode = outline.get() && fill.get() ? ShapeMode.Both
                    : outline.get() ? ShapeMode.Lines
                    : fill.get() ? ShapeMode.Sides : null;

                if (mode != null) {
                    event.renderer.box(pos, c, side, mode, 0);
                }

                if (tracers.get()) {
                    event.renderer.line(
                        mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        c
                    );
                }
            }
        }
    }
}
