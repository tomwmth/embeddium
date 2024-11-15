package me.jellysquid.mods.sodium.mixin.features.textures.animations.upload;

import me.jellysquid.mods.sodium.client.util.NativeImageHelper;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.platform.NativeImage;

import java.util.List;

@Mixin(SpriteContents.InterpolationData.class)
public class SpriteContentsInterpolationMixin {
    @Shadow
    @Final
    private NativeImage[] activeFrame;

    @Unique
    private SpriteContents parent;

    @Unique
    private static final int STRIDE = 4;

    /**
     * @author IMS
     * @reason Replace fragile Shadow
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    public void assignParent(SpriteContents parent, CallbackInfo ci) {
        this.parent = parent;
    }

    /**
     * @author JellySquid
     * @reason Drastic optimizations
     */
    @Overwrite
    void uploadInterpolatedFrame(int x, int y, SpriteContents.Ticker ticker) {
        SpriteContents.AnimatedTexture animationInfo = ticker.animationInfo;
        List<SpriteContents.FrameInfo> frames = animationInfo.frames;
        SpriteContents.FrameInfo frameInfo = frames.get(ticker.frame);

        int curIndex = frameInfo.index;
        int nextIndex = frames.get((ticker.frame + 1) % frames.size()).index;

        if (curIndex == nextIndex) {
            return;
        }

        // The mix factor between the current and next frame
        float mix = 1.0F - (float) ticker.subFrame / (float) frameInfo.time;

        for (int layer = 0; layer < this.activeFrame.length; layer++) {
            int width = this.parent.width() >> layer;
            int height = this.parent.height() >> layer;

            int curX = ((curIndex % animationInfo.frameRowSize) * width);
            int curY = ((curIndex / animationInfo.frameRowSize) * height);

            int nextX = ((nextIndex % animationInfo.frameRowSize) * width);
            int nextY = ((nextIndex / animationInfo.frameRowSize) * height);

            NativeImage src = this.parent.byMipLevel[layer];
            NativeImage dst = this.activeFrame[layer];

            long ppSrcPixel = NativeImageHelper.getPointerRGBA(src);
            long ppDstPixel = NativeImageHelper.getPointerRGBA(dst);

            for (int layerY = 0; layerY < height; layerY++) {
                // Pointers to the pixel array for the current and next frame
                long pRgba1 = ppSrcPixel + (curX + (long) (curY + layerY) * src.getWidth()) * STRIDE;
                long pRgba2 = ppSrcPixel + (nextX + (long) (nextY + layerY) * src.getWidth()) * STRIDE;

                for (int layerX = 0; layerX < width; layerX++) {
                    int rgba1 = MemoryUtil.memGetInt(pRgba1);
                    int rgba2 = MemoryUtil.memGetInt(pRgba2);

                    // Mix the RGB components and truncate the A component
                    int mixedRgb = ColorMixer.mix(rgba1, rgba2, mix) & 0x00FFFFFF;

                    // Take the A component from the source pixel
                    int alpha = rgba1 & 0xFF000000;

                    // Update the pixel within the interpolated frame using the combined RGB and A components
                    MemoryUtil.memPutInt(ppDstPixel, mixedRgb | alpha);

                    pRgba1 += STRIDE;
                    pRgba2 += STRIDE;

                    ppDstPixel += STRIDE;
                }
            }
        }

        this.parent.upload(x, y, 0, 0, this.activeFrame);
    }
}
