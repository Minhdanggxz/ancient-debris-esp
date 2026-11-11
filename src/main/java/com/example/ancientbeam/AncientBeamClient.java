package com.example.ancientbeam;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AncientBeamClient implements ClientModInitializer {
    private static KeyBinding toggleKey, cycleKey, soundKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ancientbeam.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.ancientbeam"));
        cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ancientbeam.cycle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.ancientbeam"));

        soundKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ancientbeam.sound", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.ancientbeam"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.wasPressed()) {
                Debris.enabled = !Debris.enabled;
                if (mc.player != null)
                    mc.player.sendMessage(Text.literal("§b[AncientBeam] §f" + (Debris.enabled ? "BẬT" : "TẮT")), true);
            }
            while (cycleKey.wasPressed()) Debris.cycle();
            while (soundKey.wasPressed()) {
                Debris.sound = !Debris.sound;
                if (mc.player != null)
                    mc.player.sendMessage(Text.literal("§b[AncientBeam] §fÂm thanh: " + (Debris.sound ? "BẬT" : "TẮT")), true);
            }
            Debris.tick(mc);
        });
        WorldRenderEvents.LAST.register(DebrisRenderer::render);
        HudRenderCallback.EVENT.register(DebrisHud::render);
    }
}
