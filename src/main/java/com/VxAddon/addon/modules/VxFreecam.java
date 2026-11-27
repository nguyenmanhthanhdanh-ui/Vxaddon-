
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class VxFreecam extends Module {
    private boolean enabledFreecam;

    public VxFreecam() {
        super(
            VxAddon.CATEGORY,
            "freecam-mining",
            "Toggles Meteor Freecam."
        );
    }

    @Override
    public void onActivate() {
        meteordevelopment.meteorclient.systems.modules.render.Freecam module =
            Modules.get().get(
                meteordevelopment.meteorclient.systems.modules.render.Freecam.class
            );

        if (module == null) {
            error("Meteor Freecam not found.");
            toggle();
            return;
        }

        enabledFreecam = !module.isActive();

        if (enabledFreecam) module.toggle();
    }

    @Override
    public void onDeactivate() {
        meteordevelopment.meteorclient.systems.modules.render.Freecam module =
            Modules.get().get(
                meteordevelopment.meteorclient.systems.modules.render.Freecam.class
            );

        if (enabledFreecam && module != null && module.isActive()) {
            module.toggle();
        }

        enabledFreecam = false;
    }
}
