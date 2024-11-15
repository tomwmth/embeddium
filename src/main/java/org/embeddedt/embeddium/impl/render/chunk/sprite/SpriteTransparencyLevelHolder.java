package org.embeddedt.embeddium.impl.render.chunk.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public interface SpriteTransparencyLevelHolder {
    SpriteTransparencyLevel embeddium$getTransparencyLevel();

    static SpriteTransparencyLevel getTransparencyLevel(TextureAtlasSprite sprite) {
        return ((SpriteTransparencyLevelHolder) sprite.contents()).embeddium$getTransparencyLevel();
    }
}
