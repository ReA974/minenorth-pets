package fr.minenorth.pets;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Pendant un combat consenti, l'animal va vers son adversaire (priorité 0 : passe devant tout le reste). Les dégâts sont gérés par PetFight. */
public class FightGoal extends Goal {
    private final TamableAnimal pet;

    public FightGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() { return PetFight.isFighting(pet) && PetFight.opponentOf(pet) != null; }
    @Override public boolean canContinueToUse() { return canUse(); }

    @Override
    public void tick() {
        TamableAnimal opponent = PetFight.opponentOf(pet);
        if (opponent == null) return;
        pet.getLookControl().setLookAt(opponent, 30f, 30f);
        if (pet.distanceToSqr(opponent) > 2.0 * 2.0) {
            if (pet.tickCount % 4 == 0) pet.getNavigation().moveTo(opponent, 1.25);
        } else {
            pet.getNavigation().stop();
        }
    }
}
