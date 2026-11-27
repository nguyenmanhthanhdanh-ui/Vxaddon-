
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class VXFreecam extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> speed = sgGeneral.add(
        new IntSetting.Builder()
            .name("speed")
            .description("Camera movement speed.")
            .defaultValue(1000)
            .min(100)
            .max(10000)
            .sliderMin(100)
            .sliderMax(10000)
            .build()
    );

    private boolean activatedMeteorFreecam = false;

    public VXFreecam() {
        super(
            VxAddon.CATEGORY,
            "vx-freecam",
            "Controls Meteor Freecam."
        );
    }

    @Override
    public void onActivate() {
        var freecam = Modules.get().get(
            meteordevelopment.meteorclient.systems.modules.render.Freecam.class
        );

        if (freecam == null) {
            error("Meteor Freecam not found.");
            toggle();
            return;
        }

        if (!freecam.isActive()) {
            freecam.toggle();
            activatedMeteorFreecam = true;
        }
    }

    @Override
    public void onDeactivate() {
        var freecam = Modules.get().get(
            meteordevelopment.meteorclient.systems.modules.render.Freecam.class
        );

        if (activatedMeteorFreecam && freecam != null && freecam.isActive()) {
            freecam.toggle();
        }

        activatedMeteorFreecam = false;
    }
}
