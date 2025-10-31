package com.whisent.kubeloader.utils;

import dev.latvian.mods.kubejs.typings.Info;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class KLUtil {
//    public static String test() {
//        String testEvent = """
//                StartupEvents.init(event=>{
//                    console.log("test")
//                })
//                """;
//        //TestEventProbeFix.main(null);
//
//        return testEvent;
//    }
    private static RegistryAccess clientRegistryAccess = null;
    @Info("to generate UUID by any string.")
    public static UUID genUUID(String string) {
        return UUID.nameUUIDFromBytes(string.getBytes());
    }
    @HideFromJS
    public static void setClientRegistryAccess(RegistryAccess registryAccess) {
        clientRegistryAccess = registryAccess;
    }

    public static RegistryAccess getClientRegistryAccess() {
        return clientRegistryAccess;
    }
    public static RegistryAccess getServerRegistryAccess() {
        return UtilsJS.staticRegistryAccess;
    }
    public static SimpleContainer newSimpleContainer(int size) {
        return new SimpleContainer(size);
    }
    public static void tridentRiptide(Player player, int riptideLevel, Level level) {
        float f7 = player.getYRot();
        float f = player.getXRot();
        float f1 = -Mth.sin(f7 * ((float)Math.PI / 180F)) * Mth.cos(f * ((float)Math.PI / 180F));
        float f2 = -Mth.sin(f * ((float)Math.PI / 180F));
        float f3 = Mth.cos(f7 * ((float)Math.PI / 180F)) * Mth.cos(f * ((float)Math.PI / 180F));
        float f4 = Mth.sqrt(f1 * f1 + f2 * f2 + f3 * f3);
        float f5 = 3.0F * ((1.0F + (float)riptideLevel) / 4.0F);
        f1 *= f5 / f4;
        f2 *= f5 / f4;
        f3 *= f5 / f4;
        player.push((double)f1, (double)f2, (double)f3);
        player.startAutoSpinAttack(20);
        if (player.onGround()) {
            float f6 = 1.1999999F;
            player.move(MoverType.SELF, new Vec3(0.0D, (double)1.1999999F, 0.0D));
        }

        SoundEvent soundevent;
        if (riptideLevel >= 3) {
            soundevent = SoundEvents.TRIDENT_RIPTIDE_3;
        } else if (riptideLevel == 2) {
            soundevent = SoundEvents.TRIDENT_RIPTIDE_2;
        } else {
            soundevent = SoundEvents.TRIDENT_RIPTIDE_1;
        }

        level.playSound((Player)null, player, soundevent, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}