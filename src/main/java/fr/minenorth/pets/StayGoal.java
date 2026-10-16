package fr.minenorth.pets;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * « Rester ici » : tant que l'animal est marqué, il garde la place (priorité 2, passe devant la promenade au hasard et le suivi du maître).
 * Le K9 (priorité 1) peut toujours s'interrompre pour aller vers une personne qui porte de la drogue.
 */
public class StayGoal extends Goal {
    private final TamableAnimal pet;

    public StayGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public boolean canUse() { return pet.isTame() && PetActions.isStaying(pet) && !pet.isPassenger(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void start() { pet.getNavigation().stop(); }
}
