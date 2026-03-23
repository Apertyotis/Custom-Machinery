package fr.frinn.custommachinery.common.compat.create;

import net.minecraft.world.level.Level;

public class SchematicWorld {
    public static final Class<?> SCHEMATIC_WORLD = init();

    private static Class<?> init() {
        Class<?> clazz;
        try {
            // 0.5.x
            clazz = Class.forName("com.simibubi.create.content.schematics.SchematicWorld");
        } catch (ClassNotFoundException e) {
            try {
                // 6.0.x
                clazz = Class.forName("net.createmod.catnip.levelWrappers.SchematicLevel");
            } catch (ClassNotFoundException e1) {
                return null;
            }
        }
        return clazz;
    }

    public static boolean is(Level world) {
        return SCHEMATIC_WORLD != null && SCHEMATIC_WORLD.isInstance(world);
    }
}
