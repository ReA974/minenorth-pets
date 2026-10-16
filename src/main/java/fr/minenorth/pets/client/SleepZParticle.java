package fr.minenorth.pets.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Petit « Z » qui monte doucement et s'efface. */
public class SleepZParticle extends TextureSheetParticle {
    private SleepZParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        pickSprite(sprites);
        lifetime = 50;
        quadSize = 0.14f;
        xd = 0.004;
        yd = 0.03;
        zd = 0.004;
        gravity = 0;
        hasPhysics = false;
    }

    @Override
    public void tick() {
        super.tick();
        alpha = Math.max(0f, 1f - (float) age / lifetime);
        quadSize += 0.002f;
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new SleepZParticle(level, x, y, z, sprites);
        }
    }
}
