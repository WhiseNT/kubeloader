package com.whisent.kubeloader.mixin;

import dev.latvian.mods.rhino.util.HideFromJS;
import dev.latvian.mods.rhino.util.RemapForJS;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ServerPlayer.class)
public class ServerPlayerMixin {

    @Unique
    @RemapForJS("playsound")
    public void kubeLoader$playsound(SoundEvent sound, float volume, float pitch) {
        if (!thiz().level().isClientSide) {
            Holder<SoundEvent> holder = Holder.direct(sound);
            thiz().connection.send(new ClientboundSoundPacket(holder,thiz().getSoundSource(),thiz().getX(),thiz().getY(),thiz().getZ(),
                    volume,pitch,thiz().level().getRandom().nextLong()));
        }
    }

    @Unique
    @RemapForJS("showActionbar")
    public void kubeLoader$sendActionBarMessage(Component message) {
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(message);
        thiz().connection.send(packet);
    }
    @Unique
    @RemapForJS("showTitle")
    public void kubeLoader$sendTitleMessage(Component message) {
        thiz().connection.send(new ClientboundSetTitleTextPacket(message));
    }
    @Unique
    @RemapForJS("showTitle")
    public void kubeLoader$sendTitleMessage(Component message, int fadeIn, int stay, int fadeOut) {
        thiz().connection.send(new ClientboundSetTitleTextPacket(message));
        thiz().connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
    }
    @Unique
    @RemapForJS("showSubTitle")
    public void kubeLoader$sendSubTitleMessage(Component message) {
        thiz().connection.send(new ClientboundSetSubtitleTextPacket(message));
    }
    @HideFromJS
    public ServerPlayer thiz() {
        return (ServerPlayer)(Object)this;
    }
}
