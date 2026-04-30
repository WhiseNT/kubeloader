package com.whisent.kubeloader.graal.event;

import dev.latvian.mods.kubejs.forge.ForgeEventConsumer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;

public class GraalForgeEventWrapper {
    private IEventBus bus;

    public GraalForgeEventWrapper(IEventBus bus) {
        this.bus = bus;
    }

    public void onEvent(Object eventClass,ForgeEventConsumer consumer) {
        Class clazz = null;
        if (eventClass instanceof Class) {
            clazz = (Class) eventClass;
        } else {
            try {
                clazz = Class.forName(eventClass.toString());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (clazz != null) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,false,clazz,consumer);
            MinecraftForge.EVENT_BUS.start();
            System.out.println("Registered event handler for " + clazz.getName());
            System.out.println(consumer.toString());
        } else {
            throw new IllegalArgumentException("Invalid event class: " + eventClass);
        }
    }
}
