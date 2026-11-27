
package com.vxaddon.addon;

import com.mojang.logging.LogUtils;

import com.vxaddon.addon.modules.SusChunkFinder;
import com.vxaddon.addon.modules.KelpESP;
import com.vxaddon.addon.modules.StorageESP;
import com.vxaddon.addon.modules.VxBlockEntitiesESP;
import com.vxaddon.addon.modules.PlayerDebug;
import com.vxaddon.addon.modules.Freecam;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

import org.slf4j.Logger;

public class VxAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = new Category("VX");

    @Override
    public void onInitialize() {
        LOG.info("Initializing VX Addon");

        Modules modules = Modules.get();

        modules.add(new SusChunkFinder());
        modules.add(new KelpESP());
        modules.add(new StorageESP());
        modules.add(new BlockEntitiesESP());
        modules.add(new PlayerDebug());
        modules.add(new Freecam());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.vxaddon.addon";
    }
}
