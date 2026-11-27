
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;

public class FreecamMining extends Module {
    private boolean enabledFreecam;

    public FreecamMining() {
        super(
            VxAddon.CATEGORY,
            "freecam-mining",
            "Enables Meteor Freecam while keeping normal player interaction range."
        );
    }

    @Override
    public void onActivate() {
        Freecam freecam = Modules.get().get(Freecam.class);

        if (freecam == null) {
            error("Meteor Freecam module was not found.");
            toggle();
            return;
        }

        enabledFreecam = !freecam.isActive();

        if (enabledFreecam) {
            freecam.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        Freecam freecam = Modules.get().get(Freecam.class);

        if (enabledFreecam && freecam != null && freecam.isActive()) {
            freecam.toggle();
        }

        enabledFreecam = false;
    }
            }
