package fr.minenorth.pets;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /pets certificat <espece> : donne un certificat d'adoption (admin), à mettre en vente dans une boutique de minenorth_shops. */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID)
public final class PetCommands {
    private PetCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("pets").requires(s -> s.hasPermission(2))
                .then(Commands.literal("certificat")
                        .then(Commands.argument("espece", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ModItems.speciesIds().stream().map(ResourceLocation::toString), b))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    ResourceLocation id = ResourceLocation.tryParse(StringArgumentType.getString(c, "espece"));
                                    if (id == null || !ModItems.speciesIds().contains(id)) {
                                        c.getSource().sendFailure(Component.literal("Espèce inconnue (exemple : polly_pets:german_shepherd)."));
                                        return 0;
                                    }
                                    p.getInventory().placeItemBackInInventory(AdoptionItem.of(id));
                                    return 1;
                                }))));
    }
}
