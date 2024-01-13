package me.jellysquid.mods.sodium.client.util;

import me.jellysquid.mods.sodium.client.util.color.ColorARGB;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.util.math.Direction;

/**
 * Provides some utilities and constants for interacting with vanilla's model quad vertex format.
 *
 * This is the current vertex format used by Minecraft for chunk meshes and model quads. Internally, it uses integer
 * arrays for store baked quad data, and as such the following table provides both the byte and int indices.
 *
 * Byte Index    Integer Index             Name                 Format                 Fields
 * 0 ..11        0..2                      Position             3 floats               x, y, z
 * 12..15        3                         Color                4 unsigned bytes       a, r, g, b
 * 16..23        4..5                      Block Texture        2 floats               u, v
 * 24..27        6                         Light Texture        2 shorts               u, v
 * 28..30        7                         Normal               3 unsigned bytes       x, y, z
 * 31                                      Padding              1 byte
 */
public class ModelQuadUtil {
    // Integer indices for vertex attributes, useful for accessing baked quad data
    public static final int POSITION_INDEX = 0,
            COLOR_INDEX = 3,
            TEXTURE_INDEX = 4,
            LIGHT_INDEX = 6,
            NORMAL_INDEX = 7;

    // Size of vertex format in 4-byte integers
    public static final int VERTEX_SIZE = 8;

    // Cached array of normals for every facing to avoid expensive computation
    static final int[] NORMALS = new int[DirectionUtil.ALL_DIRECTIONS.length];

    static {
        for (int i = 0; i < NORMALS.length; i++) {
            NORMALS[i] = Norm3b.pack(DirectionUtil.ALL_DIRECTIONS[i].getVector());
        }
    }

    /**
     * Returns the normal vector for a model quad with the given {@param facing}.
     */
    public static int getFacingNormal(Direction facing) {
        return NORMALS[facing.ordinal()];
    }

    public static int getFacingNormal(Direction facing, int bakedNormal) {
        if(!hasNormal(bakedNormal))
            return NORMALS[facing.ordinal()];
        return bakedNormal;
    }

    public static boolean hasNormal(int n) {
        return (n & 0xFFFFFF) != 0;
    }

    /**
     * @param vertexIndex The index of the vertex to access
     * @return The starting offset of the vertex's attributes
     */
    public static int vertexOffset(int vertexIndex) {
        return vertexIndex * VERTEX_SIZE;
    }

    public static int mergeBakedLight(int packedLight, int calcLight) {
        // bail early in most cases
        if (packedLight == 0)
            return calcLight;

        int psl = (packedLight >> 16) & 0xFF;
        int csl = (calcLight >> 16) & 0xFF;
        int pbl = (packedLight) & 0xFF;
        int cbl = (calcLight) & 0xFF;
        int bl = Math.max(pbl, cbl);
        int sl = Math.max(psl, csl);
        return (sl << 16) | bl;
    }

    /**
     * Mixes two ABGR colors together like what Forge does in VertexConsumer.
     *
     * Despite the name, the method tries to avoid doing any work whenever possible.
     */
    public static int mixARGBColors(int colorA, int colorB) {
        // Most common case: Either quad coloring or tint-based coloring, but not both
        if (colorA == -1) {
            return colorB;
        } else if (colorB == -1) {
            return colorA;
        }
        // General case (rare): Both colorings, actually perform the multiplication
        int a = (int)((ColorARGB.unpackAlpha(colorA)/255.0f) * (ColorARGB.unpackAlpha(colorB)/255.0f) * 255.0f);
        int b = (int)((ColorARGB.unpackBlue(colorA)/255.0f) * (ColorARGB.unpackBlue(colorB)/255.0f) * 255.0f);
        int g = (int)((ColorARGB.unpackGreen(colorA)/255.0f) * (ColorARGB.unpackGreen(colorB)/255.0f) * 255.0f);
        int r = (int)((ColorARGB.unpackRed(colorA)/255.0f) * (ColorARGB.unpackRed(colorB)/255.0f) * 255.0f);
        return ColorARGB.pack(r, g, b, a);
    }
}
