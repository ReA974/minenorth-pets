package fr.minenorth.pets;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MineNorthPets.MOD_ID);

    /** Petit « Z » qui monte au-dessus d'un animal qui dort. */
    public static final RegistryObject<SimpleParticleType> SLEEP_Z = PARTICLES.register("sleep_z", () -> new SimpleParticleType(true));

    public static void register(IEventBus modBus) { PARTICLES.register(modBus); }
}
