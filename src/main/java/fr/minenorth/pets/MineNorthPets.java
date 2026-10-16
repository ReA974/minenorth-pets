package fr.minenorth.pets;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * MineNorth Pets : complément de Polly Pets (mod sans sources, branché par événements).
 * Les animaux apprivoisés suivent leur maître et s'assoient sur ordre ; la police équipe un chien en chien K9 qui renifle la drogue.
 */
@Mod(MineNorthPets.MOD_ID)
public class MineNorthPets {
    public static final String MOD_ID = "minenorthpets";

    public MineNorthPets() {
        ModItems.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModParticles.register(FMLJavaModLoadingContext.get().getModEventBus());
        K9Net.register();
        // Config du serveur dédié seulement, dans config/ (le client ne crée aucun fichier).
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PetsConfig.SPEC, "minenorth_pets.toml");
        }
    }
}
