package com.ponderpp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ponderpp.PonderUIExt;

import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Forge 1.20.1 平台：在 {@code PonderUI#renderBackground} 末尾绘制暂停/播放按钮。
 *
 * <p>1.20.1 的 {@code PonderUI} override 了原版 {@code renderBackground}（SRG 可重映射），
 * 因此它能可靠地作为渲染锚点（与共享逻辑 Mixin 的 {@code tick} / {@code removed} 一样）。</p>
 */
@Mixin(PonderUI.class)
public abstract class ForgePonderUIRenderMixin {

	@Inject(method = "renderBackground", at = @At("TAIL"))
	private void ponderpp$renderButton(GuiGraphics graphics, CallbackInfo ci) {
		PonderUI self = (PonderUI) (Object) this;
		PonderUIExt.renderButton(self, graphics);
	}
}
