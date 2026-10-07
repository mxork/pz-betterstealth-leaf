package pz.mxork;

import dev.aoqia.leaf.api.ModInitializer;

import zombie.debug.DebugType;

public class Entry implements ModInitializer {
    public static final String MOD_ID = "betterstealth";
    public static final DebugType LOGGER = DebugType.General;

    @Override
    public void onInitialize() {
        LOGGER.debugln("[%s] %s", MOD_ID, "loaeded");
    }
}
