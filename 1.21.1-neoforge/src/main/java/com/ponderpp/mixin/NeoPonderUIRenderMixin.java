package com.ponderpp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ponderpp.PonderUIExt;

import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;

/**
 * NeoForge 1.21.1 平台：在 {@code PonderUI#renderWindow} 末尾绘制暂停/播放按钮。
 *
 * <p>NeoForge 运行于官名(NeoForm)映射，无 refmap；而 1.21.1 的 {@code PonderUI} 不再
 * override 原版 {@code renderBackground}（该方法在 1.21.1 已被 NeoForge 改用带背景参数
 * 的新签名），因此改用 PonderUI 自身存在的 {@code renderWindow} 作为渲染锚点。
 * {@code remap=false} 明确不做重映射，直接按官名匹配。</p>
 */
@Mixin(PonderUI.class)
public abstract class NeoPonderUIRenderMixin {

	@Inject(method = "renderWindow", at = @At("TAIL"), remap = false)
	private void ponderpp$renderButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
		CallbackInfo ci) {
		PonderUI self = (PonderUI) (Object) this;
		PonderUIExt.renderButton(self, graphics);
	}
}
