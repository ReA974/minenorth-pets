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

    private static final String PROTOCOL = "6";
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

    /** Dit au joueur s'il porte un animal (le client n'intercepte Ctrl + clic dans le vide que dans ce cas). */
    public record CarrySync(boolean carrying) {
        void encode(FriendlyByteBuf b) { b.writeBoolean(carrying); }
        static CarrySync decode(FriendlyByteBuf b) { return new CarrySync(b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.pets.client.ClientK9.setCarrying(carrying)));
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
                // entityId -1 : Ctrl + clic dans le vide pendant qu'on porte un animal -> menu de l'animal porté.
                TamableAnimal pet = p == null ? null : entityId == -1 ? PetCarry.carriedBy(p) : PetActions.targetAny(p, entityId);
                if (pet == null) return;
                int flags = PetActions.menuFlags(p, pet);
                if (flags == 0) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.literal("§7Ce n'est pas votre animal."), true);
                    return;
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenMenu(pet.getId(), flags));
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
                if (p != null && action >= 0 && action < all.length) PetActions.performChecked(p, entityId, all[action]);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Remarque ajoutée au carnet par un vétérinaire (le serveur revérifie le droit et nettoie le texte). */
    public record Remark(int entityId, String text) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeUtf(text, 200); }
        static Remark decode(FriendlyByteBuf b) { return new Remark(b.readVarInt(), b.readUtf(200)); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                TamableAnimal pet = p == null ? null : PetActions.targetAny(p, entityId);
                String clean = PetBook.clean(text);
                if (pet == null || clean.isEmpty() || !Compat.isVet(p)) return;
                PetBook.remark(pet, clean);
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("§aRemarque ajoutée au carnet de santé."), true);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Fiche du carnet de santé de poche (avec les boutons « perdu / retrouvé » et « abandonner »). */
    public record BookOpen(java.util.List<String> lines, boolean lost) {
        void encode(FriendlyByteBuf b) { b.writeCollection(lines, (buf, line) -> buf.writeUtf(line, 300)); b.writeBoolean(lost); }
        static BookOpen decode(FriendlyByteBuf b) { return new BookOpen(b.readList(buf -> buf.readUtf(300)), b.readBoolean()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.pets.client.PetBookScreen.openWithActions(lines, lost)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Bouton de la fiche du carnet de poche : 0 = perdu / retrouvé, 1 = abandonner (confirmation côté serveur). */
    public record BookAction(int kind) {
        void encode(FriendlyByteBuf b) { b.writeVarInt(kind); }
        static BookAction decode(FriendlyByteBuf b) { return new BookAction(b.readVarInt()); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null) PetCarnet.action(p, kind);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Contenu du carnet de santé, envoyé au joueur qui le demande. */
    public record Book(java.util.List<String> lines) {
        void encode(FriendlyByteBuf b) { b.writeCollection(lines, (buf, line) -> buf.writeUtf(line, 300)); }
        static Book decode(FriendlyByteBuf b) { return new Book(b.readList(buf -> buf.readUtf(300))); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.minenorth.pets.client.PetBookScreen.open(lines)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void register() {
        CHANNEL.registerMessage(0, Sync.class, Sync::encode, Sync::decode, Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, SleepSync.class, SleepSync::encode, SleepSync::decode, SleepSync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, OpenMenu.class, OpenMenu::encode, OpenMenu::decode, OpenMenu::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, OpenRequest.class, OpenRequest::encode, OpenRequest::decode, OpenRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, Action.class, Action::encode, Action::decode, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, Remark.class, Remark::encode, Remark::decode, Remark::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, CarrySync.class, CarrySync::encode, CarrySync::decode, CarrySync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, BookOpen.class, BookOpen::encode, BookOpen::decode, BookOpen::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(9, BookAction.class, BookAction::encode, BookAction::decode, BookAction::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, Book.class, Book::encode, Book::decode, Book::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendCarry(ServerPlayer p, boolean carrying) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CarrySync(carrying));
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
