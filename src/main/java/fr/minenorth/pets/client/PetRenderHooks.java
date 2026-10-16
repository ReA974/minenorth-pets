package fr.minenorth.pets.client;

import com.mojang.logging.LogUtils;
import fr.minenorth.pets.MineNorthPets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import org.slf4j.Logger;

import java.lang.reflect.Field;

/**
 * GeckoLib compile les couches d'un rendu à sa première utilisation : on en profite, pour chaque rendu de Polly Pets, pour y ajouter le
 * harnais K9 et remplacer son modèle par une enveloppe qui sait asseoir l'animal.
 */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID, value = Dist.CLIENT)
public final class PetRenderHooks {
    private PetRenderHooks() {}

    static final Logger LOGGER = LogUtils.getLogger();
    private static int installed;
    private static Field modelField;
    private static boolean modelFieldFailed;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void onCompileLayers(GeoRenderEvent.Entity.CompileRenderLayers ev) {
        GeoEntityRenderer renderer = ev.getRenderer();
        GeoModel model = renderer.getGeoModel();
        if (!model.getClass().getName().startsWith("net.mcreator.pollypets.")) return;
        ev.addLayer(new K9HarnessLayer(renderer));
        if (installed++ < 3) LOGGER.info("[MineNorthPets] harnais K9 + pose assise installés sur {} (modèle {})", renderer.getClass().getName(), model.getClass().getName());
        if (model instanceof SitModel || modelFieldFailed) return;
        try {
            if (modelField == null) {
                modelField = GeoEntityRenderer.class.getDeclaredField("model");
                modelField.setAccessible(true);
            }
            modelField.set(renderer, new SitModel(model));
        } catch (Throwable t) {
            // Si GeckoLib change ce champ : pas de pose assise, mais le harnais fonctionne toujours.
            modelFieldFailed = true;
            LOGGER.error("[MineNorthPets] impossible de remplacer le modèle GeckoLib : pas de pose assise", t);
        }
    }
}
