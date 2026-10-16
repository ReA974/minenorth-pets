package fr.minenorth.pets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Canal réseau du mod.
 * - Sync / SleepSync : états K9 et « couché » d'un animal (stockés côté serveur), envoyés aux joueurs qui le suivent et à ceux qui
 *   commencent à le suivre, pour que le client dessine le harnais et la pose couchée.
 * - OpenRequest / OpenMenu : Ctrl + clic droit sur son animal -> le serveur renvoie les états et le client ouvre le menu.
 * - Action : un bouton du menu ; le serveur revérifie que le joueur est le maître et à portée.
 */
@Mod.EventBusSubscriber(modid = MineNorthPets.MOD_ID)
public final class K9Net {
    private K9Net() {}

    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthPets.MOD_ID, "k9"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    // ------------------------------------------------------------------ serveur -> client

    public record Sync(int entityId, boolean k9) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeBoolean(k9); }
        static Sync decode(FriendlyByteBuf b) { return new Sync(b.readVarInt(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.pets.client.ClientK9.set(entityId, k9)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SleepSync(int entityId, boolean sleeping) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeBoolean(sleeping); }
        static SleepSync decode(FriendlyByteBuf b) { return new SleepSync(b.readVarInt(), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.pets.client.ClientK9.setSleeping(entityId, sleeping)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record OpenMenu(int entityId, int flags) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeVarInt(flags); }
        static OpenMenu decode(FriendlyByteBuf b) { return new OpenMenu(b.readVarInt(), b.readVarInt()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.pets.client.PetMenuScreen.open(entityId, flags)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ client -> serveur

    public record OpenRequest(int entityId) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(entityId); }
        static OpenRequest decode(FriendlyByteBuf b) { return new OpenRequest(b.readVarInt()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                TamableAnimal pet = p == null ? null : PetActions.target(p, entityId);
                if (pet != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenMenu(entityId, PetActions.flags(pet)));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Action(int entityId, int action) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeVarInt(action); }
        static Action decode(FriendlyByteBuf b) { return new Action(b.readVarInt(), b.readVarInt()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                PetActions.Action[] all = PetActions.Action.values();
                if (p == null || action < 0 || action >= all.length) return;
                TamableAnimal pet = PetActions.target(p, entityId);
                if (pet != null) PetActions.perform(p, pet, all[action]);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static void register() {
        CHANNEL.registerMessage(0, Sync.class, Sync::encode, Sync::decode, Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, SleepSync.class, SleepSync::encode, SleepSync::decode, SleepSync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, OpenMenu.class, OpenMenu::encode, OpenMenu::decode, OpenMenu::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, OpenRequest.class, OpenRequest::encode, OpenRequest::decode, OpenRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, Action.class, Action::encode, Action::decode, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** À appeler après un changement de statut K9. */
    public static void broadcast(Entity pet, boolean k9) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> pet), new Sync(pet.getId(), k9));
    }

    /** À appeler quand un animal se couche ou se réveille. */
    public static void broadcastSleep(Entity pet, boolean sleeping) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> pet), new SleepSync(pet.getId(), sleeping));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Entity t = ev.getTarget();
        if (PetEvents.isK9(t)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Sync(t.getId(), true));
        if (PetActions.isSleeping(t)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SleepSync(t.getId(), true));
    }
}
