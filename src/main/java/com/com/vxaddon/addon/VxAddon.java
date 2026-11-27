
package com.vxaddon.addon;

import com.mojang.logging.LogUtils;
import com.vxaddon.addon.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class VxAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("VX");

    @Override
    public void onInitialize() {
        Modules modules = Modules.get();
        modules.add(new SusChunkFinder());
        modules.add(new KelpESP());
        modules.add(new AutoBuildBot());
        modules.add(new StorageESP());
        modules.add(new BlockEntitiesESP());
        modules.add(new SwingSpeed());
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
