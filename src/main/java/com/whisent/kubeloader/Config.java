package com.whisent.kubeloader;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@EventBusSubscriber(modid = Kubeloader.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue DEBUG = BUILDER.comment("Enable debug mode").define("debug", false);

    // ModernJSParser configuration options
        private static final ModConfigSpec.BooleanValue SPREAD_OPERATOR = BUILDER
            .comment("Enable spread operator (...) to ES5 transformation")
            .define("spreadOperator", true);

        static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean debug;
    
    // ModernJSParser configuration values
    public static boolean spreadOperator;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        debug = DEBUG.get();
        spreadOperator = SPREAD_OPERATOR.get();
    }
}