package com.whisent.kubeloader.utils;

import com.whisent.kubeloader.TestEventProbeFix;
import com.whisent.kubeloader.mixinjs.dsl.*;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.ast.FunctionNode;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KLUtil {
    public static String test() {
        String testEvent = """
                StartupEvents.init(event=>{
                    console.log("test")
                })
                """;
        TestEventProbeFix.main(null);

        return testEvent;
    }

    public static void test(String[] args) {
        System.out.println("开始解析MixinDSL...");
        String dslCode = "Mixin.type('FunctionDeclaration')\n" +
                "    .at('head')\n" +
                "    .in('test')\n" +
                "    .inject(function testMixin() {\n" +
                "        console.log('123Inject')\n" +
                "    })";
        List<MixinDSL> dsls = MixinDSLParser.parse(dslCode);
        System.out.println("DSL已解析: " + dsls);
        for (MixinDSL dsl : dsls) {
            System.out.println("  targetFile: " + dsl.getTargetFile());
            System.out.println("  targetLocation: " + dsl.getTargetLocation());
            System.out.println("  type: " + dsl.getType());
            System.out.println("  at: " + dsl.getAt());
            System.out.println("  target: " + dsl.getTarget());
            System.out.println("  action: " + dsl.getAction());
        }
        System.out.println("解析完成，DSL数量: " + dsls.size());
    }
    public static Object convertFunction(Object function) {
        if (function instanceof Function) {
            // 如果是函数对象，将其转换为字符串并返回
            //AstNode node = ((FunctionNode) function).getAstRoot();
            
            System.out.println("函数对象" + ((Function)function).toString());

            return Context.javaToJS( Context.enter(),function, null);
        }
        if (function instanceof FunctionNode functionNode) {
            // 使用Rhino内置的toSource方法获取源码
            return functionNode.getBody().getString();
        }
        System.out.println("其他的对象" + function);
        // 如果是其他类型的函数对象，返回其toString表示
        return function.toString();
    };
    private static RegistryAccess clientRegistryAccess = null;

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
