
package com.vxaddon.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class KelpESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> distance = sgGeneral.add(
        new IntSetting.Builder()
            .name("distance")
            .description("Kelp scanning distance.")
            .defaultValue(16)
            .min(4)
            .max(32)
            .sliderMax(32)
            .build()
    );

    private final Setting<Integer> verticalRange = sgGeneral.add(
        new IntSetting.Builder()
            .name("vertical-range")
            .description("Vertical scanning range.")
            .defaultValue(16)
            .min(4)
            .max(64)
            .sliderMax(64)
            .build()
    );

    private final Setting<Integer> maxChecks = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-checks")
            .defaultValue(50000)
            .min(1000)
            .max(150000)
            .sliderMax(150000)
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
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .build()
    );

    private final List<BlockPos> kelpBlocks = new ArrayList<>();

    public KelpESP() {
        super(
            com.vxaddon.addon.VxAddon.CATEGORY,
            "kelp-esp",
            "Highlights nearby kelp blocks."
        );
    }

    @Override
    public void onActivate() {
        kelpBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        kelpBlocks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            kelpBlocks.clear();
            return;
        }

        kelpBlocks.clear();

        BlockPos playerPos = mc.player.blockPosition();

        int radius = distance.get();
        int yRange = verticalRange.get();
        int checks = 0;

        scan:
        for (int x = playerPos.getX() - radius;
             x <= playerPos.getX() + radius; x++) {

            for (int z = playerPos.getZ() - radius;
                 z <= playerPos.getZ() + radius; z++) {

                for (int y = playerPos.getY() - yRange;
                     y <= playerPos.getY() + yRange; y++) {

                    if (checks >= maxChecks.get()) break scan;

                    checks++;

                    BlockPos pos = new BlockPos(x, y, z);

                    if (!mc.level.getBlockState(pos).is(Blocks.KELP)
                        && !mc.level.getBlockState(pos).is(Blocks.KELP_PLANT)) {
                        continue;
                    }

                    kelpBlocks.add(pos);
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        SettingColor c = color.get();

        for (BlockPos pos : kelpBlocks) {

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
