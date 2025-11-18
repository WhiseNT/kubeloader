package com.whisent.kubeloader;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = Kubeloader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue DEBUG = BUILDER.comment("Enable debug mode").define("debug", false);

    // ModernJSParser configuration options
    private static final ForgeConfigSpec.BooleanValue SPREAD_OPERATOR = BUILDER
            .comment("Enable spread operator (...) to ES5 transformation")
            .define("spreadOperator", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean debug;
    
    // ModernJSParser configuration values
    public static boolean spreadOperator;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        debug = DEBUG.get();
        spreadOperator = SPREAD_OPERATOR.get();
    }
}