package dev.nitro.util;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Client UI sound events used by NITRO. */
public final class NitroSounds {
    private static final Identifier GUI_HOVER_ID = Identifier.of("nitro", "gui_hover");
    public static final SoundEvent GUI_HOVER = SoundEvent.of(GUI_HOVER_ID);

    private NitroSounds() {}

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, GUI_HOVER_ID, GUI_HOVER);
    }
}
