package net.sistr.littlemaidrebirth.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.sistr.littlemaidrebirth.LMRBMod;
import net.sistr.littlemaidrebirth.client.IFFScreen;
import net.sistr.littlemaidrebirth.entity.iff.HasIFF;
import net.sistr.littlemaidrebirth.entity.iff.IFF;
import net.sistr.littlemaidrebirth.entity.iff.IFFTypeManager;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 対象エンティティのIFFを設定する画面を開くパケット
 * C2Sは開く要求
 * S2Cは開く命令
 */
public class OpenIFFScreenPacket {
    public static final Identifier ID =
            Identifier.of(LMRBMod.MODID, "open_iff_screen");

    public static void sendS2CPacket(Entity entity, List<IFF> iffs, PlayerEntity player,
                                     DynamicRegistryManager registryManager) {
        RegistryByteBuf buf = createS2CPacket(entity, iffs, player, registryManager);
        NetworkManager.sendToPlayer((ServerPlayerEntity) player, ID, buf);
    }

    public static RegistryByteBuf createS2CPacket(Entity entity, List<IFF> iffs, PlayerEntity player,
                                                  DynamicRegistryManager registryManager) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), registryManager);
        buf.writeVarInt(entity.getId());
        NbtCompound nbt = new NbtCompound();
        NbtList list = new NbtList();
        nbt.put("IFFs", list);
        iffs.forEach(iff -> list.add(iff.writeTag()));
        buf.writeNbt(nbt);
        return buf;
    }

    @Environment(EnvType.CLIENT)
    public static void sendC2SPacket(Entity entity, DynamicRegistryManager registryManager) {
        if (!(entity instanceof HasIFF)) {
            return;
        }
        RegistryByteBuf buf = createC2SPacket(entity, registryManager);
        NetworkManager.sendToServer(ID, buf);
    }

    public static RegistryByteBuf createC2SPacket(Entity entity, DynamicRegistryManager registryManager) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), registryManager);
        buf.writeVarInt(entity.getId());
        return buf;
    }

    @Environment(EnvType.CLIENT)
    public static void receiveS2CPacket(RegistryByteBuf buf, NetworkManager.PacketContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return;
        int id = buf.readVarInt();
        NbtCompound nbt = buf.readNbt();
        context.queue(() -> openIFFScreen(id, nbt, player));
    }

    @Environment(EnvType.CLIENT)
    private static void openIFFScreen(int id, NbtCompound nbt, PlayerEntity player) {
        Entity entity = player.getWorld().getEntityById(id);
        if (!(entity instanceof HasIFF)) {
            return;
        }
        NbtList list = nbt.getList("IFFs", 10);
        List<IFF> iffs = list.stream()
                .map(t -> (NbtCompound) t)
                .map(t -> IFFTypeManager.getINSTANCE().loadIFF(t))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        MinecraftClient.getInstance().setScreen(new IFFScreen(entity, iffs));
    }

    public static void receiveC2SPacket(RegistryByteBuf buf, NetworkManager.PacketContext context) {
        int id = buf.readVarInt();
        var registry = context.registryAccess();
        context.queue(() -> openIFFScreen(id, context.getPlayer(), registry));
    }

    private static void openIFFScreen(int id, PlayerEntity player, DynamicRegistryManager registryManager) {
        Entity entity = player.getWorld().getEntityById(id);
        if (!(entity instanceof HasIFF)
                || (entity instanceof TameableEntity
                && !player.getUuid().equals(((TameableEntity) entity).getOwnerUuid()))) {
            return;
        }
        sendS2CPacket(entity, ((HasIFF) entity).getIFFs(), player, registryManager);
    }

}
