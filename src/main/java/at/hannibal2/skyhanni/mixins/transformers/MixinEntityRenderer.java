package at.hannibal2.skyhanni.mixins.transformers;

import at.hannibal2.skyhanni.data.GuiEditManager;
import at.hannibal2.skyhanni.mixins.hooks.MouseSensitivityHook;
import at.hannibal2.skyhanni.utils.compat.DrawContext;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Inject(method = "updateCameraAndRender", at = @At("TAIL"))
    private void onLastRender(float partialTicks, long nanoTime, CallbackInfo ci) {
        GuiEditManager.renderLast(new DrawContext());
    }

    @ModifyVariable(
        method = "updateCameraAndRender",
        at = @At("STORE"),
        ordinal = 1
    )
    private float modifySensitivity(float value) {
        return MouseSensitivityHook.INSTANCE.remapSensitivity(value);
    }
}
