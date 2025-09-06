package com.whisent.kubeloader.event.kjs;

import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@Info("""
      This event will be triggered at every tick you right-click on.
      "ItemEvents.rightClicked" has a 10 tick interval.
      """)

public class KLRightclickedEventJS extends EventJS {
    public final Player player;
    public final InteractionHand hand;
    public final ItemStack item;
    public KLRightclickedEventJS(Player player, InteractionHand hand, ItemStack item) {
        this.player = player;
        this.hand = hand;
        this.item = item;
    }

}
