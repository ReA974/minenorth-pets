package fr.minenorth.pets.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Enveloppe le modèle d'un animal de Polly Pets : tout est délégué au modèle d'origine, et une fois les animations calculées, un animal
 * assis reçoit une pose assise. Polly Pets n'a une animation « sit » que pour 2 modèles sur ~45 (et ne la joue jamais) : la pose est donc
 * calculée ici à partir des os (corps = parent de la tête, pattes = os sans enfants dont le nom contient « leg »), avec les valeurs de
 * l'animation « sit » du beagle.
 */
public class SitModel<T extends GeoAnimatable> extends GeoModel<T> {
    /** Espèces dont la pose assise n'aurait pas de sens (reptiles, serpent). */
    private static final List<String> NO_SIT = List.of("snake", "gecko", "chameleon", "tortoise", "turtle");

    private final GeoModel<T> delegate;
    private boolean logged;

    public SitModel(GeoModel<T> delegate) { this.delegate = delegate; }

    @Override public ResourceLocation getModelResource(T a, @Nullable GeoRenderer<T> r) { return delegate.getModelResource(a, r); }
    @Override public ResourceLocation getModelResource(T a) { return delegate.getModelResource(a); }
    @Override public ResourceLocation getTextureResource(T a, @Nullable GeoRenderer<T> r) { return delegate.getTextureResource(a, r); }
    @Override public ResourceLocation getTextureResource(T a) { return delegate.getTextureResource(a); }
    @Override public ResourceLocation getAnimationResource(T a) { return delegate.getAnimationResource(a); }
    @Override public ResourceLocation[] getAnimationResourceFallbacks(T a) { return delegate.getAnimationResourceFallbacks(a); }
    @Override public boolean crashIfBoneMissing() { return delegate.crashIfBoneMissing(); }
    @Override public RenderType getRenderType(T a, ResourceLocation texture) { return delegate.getRenderType(a, texture); }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
        if (animatable instanceof TamableAnimal pet && pet.isInSittingPose() && canSit(pet)) pose(pet, ClientK9.isSleeping(pet.getId()));
    }

    private static boolean canSit(Entity e) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (k == null) return false;
        for (String n : NO_SIT) if (k.getPath().contains(n)) return false;
        return true;
    }

    /** Valeurs de l'animation « sit » du beagle (degrés/pixels de l'animation, convertis comme le fait GeckoLib : rotation X et Y inversées). */
    private void pose(TamableAnimal pet, boolean lying) {
        List<CoreGeoBone> legs = new ArrayList<>();
        CoreGeoBone body = null;
        for (CoreGeoBone b : getAnimationProcessor().getRegisteredBones()) {
            String n = b.getName();
            if (n.startsWith("head") && b.getParent() != null && body == null) body = b.getParent();
            if (n.contains("leg") && b.getChildBones().isEmpty()) legs.add(b);
        }
        if (body == null || legs.size() != 4) {
            if (!logged) {
                logged = true;
                PetRenderHooks.LOGGER.warn("[MineNorthPets] pas de pose assise pour {} : corps={} pattes={}", pet.getType(), body == null ? null : body.getName(), legs.size());
            }
            return;
        }
        double meanZ = legs.stream().mapToDouble(CoreGeoBone::getPivotZ).average().orElse(0);
        // Les modèles regardent vers -Z : les pattes arrière ont le pivot Z le plus grand.
        float drop = (float) (legs.stream().mapToDouble(CoreGeoBone::getPivotY).max().orElse(4) * 0.5);

        if (!logged) {
            logged = true;
            PetRenderHooks.LOGGER.info("[MineNorthPets] pose assise appliquée à {} : corps={} pattes={}", pet.getType(), body.getName(), legs.stream().map(CoreGeoBone::getName).toList());
        }
        if (lying) {
            // Couché : le ventre au sol, toutes les pattes repliées vers l'avant.
            float lie = (float) (legs.stream().mapToDouble(CoreGeoBone::getPivotY).max().orElse(4) * 0.85);
            tilt(body, 0, -lie);
            for (CoreGeoBone leg : legs) tilt(leg, 80f, -lie);
            return;
        }
        tilt(body, 15, -drop);
        for (CoreGeoBone leg : legs) {
            if (leg.getPivotZ() > meanZ) tilt(leg, 72.5f, -drop);
            else tilt(leg, 17.5f, 0);
        }
    }

    /** Pose absolue (position de départ du modèle + écart) : pas de cumul d'une image à l'autre, quoi que fasse l'animation. */
    private static void tilt(CoreGeoBone bone, float degrees, float dropPixels) {
        bone.setRotX(bone.getInitialSnapshot().getRotX() + rad(degrees));
        bone.setPosY(bone.getInitialSnapshot().getOffsetY() + dropPixels);
    }

    private static float rad(float deg) { return (float) Math.toRadians(deg); }
}
