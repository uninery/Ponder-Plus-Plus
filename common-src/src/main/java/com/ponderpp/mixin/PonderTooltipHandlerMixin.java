package com.ponderpp.mixin;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ponderpp.PonderPpSettings;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.enums.PonderKeybinds;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 需求1：把“长按 W 打开思索”改成“单击 W 即打开”（可开关）。
 *
 * <p>{@code PonderTooltipHandler#deferredTick()}（static）是“在物品栏 / 任意界面悬浮携带
 * Ponder 场景的物品时，按住 PONDER 键（默认 W）累积进度、攒满先打开思索”的实现。
 * 本 Mixin 在它执行前检测 PONDER 键的“刚按下”边沿，若开了 {@code clickToOpen} 且当前
 * 悬浮的物品确实有 Ponder 场景，则直接调用 {@link ScreenOpener#transitionTo} 打开并中断
 * 原逻辑，从而把“长按”变成“单击”。</p>
 *
 * <p>健壮性处理（对应进入存档瞬间按住 W 导致的误触发 / 崩溃）：</p>
 * <ul>
 *   <li>首次进入本逻辑时只记录按键状态、不触发打开——避免“进存档瞬间 W 一直按着”被
 *       当成“刚按下”。</li>
 *   <li>打开前必须同时满足 {@code hoveredStack}、{@code trackingStack} 非空（都是当前帧
 *       {@code updateHovered} 刚写入的悬停物品），杜绝用上一帧残留的 {@code trackingStack}
 *       打开错误场景；且当前不能处于 Ponder 界面内。</li>
 *   <li>{@code PonderUI.of()} 编译场景会写 Ponder 静态注册表，进入存档瞬间可能与后台
 *       资源注册并发导致异常，用 try/catch 兜底：本次不打开、退回原逻辑，不崩游戏。</li>
 * </ul>
 *
 * <p>{@code trackingStack}/{@code hoveredStack} static 字段用反射读取，避免依赖 SRG 映射。</p>
 */
@Mixin(PonderTooltipHandler.class)
public abstract class PonderTooltipHandlerMixin {

	/** 是否已完成首次初始化（首帧只记录按键状态，不触发打开）。 */
	@Unique
	private static boolean ponderpp$initialized = false;

	/** 上一帧 PONDER 键是否按下（“刚按下”边沿检测用）。 */
	@Unique
	private static boolean ponderpp$prevPonderDown = false;

	private static Field ponderpp$trackingField;
	private static Field ponderpp$hoveredField;

	static {
		ponderpp$trackingField = ponderpp$findField("trackingStack");
		ponderpp$hoveredField = ponderpp$findField("hoveredStack");
	}

	@Unique
	private static Field ponderpp$findField(String name) {
		try {
			Field f = PonderTooltipHandler.class.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable ignored) {
			// 版本差异导致取不到字段时，仅退回长按行为，不影响其它功能。
			return null;
		}
	}

	@Inject(method = "deferredTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void ponderpp$onDeferredTick(CallbackInfo ci) {
		boolean down = PonderKeybinds.PONDER.isDown();

		// 首帧只记录按键状态：进入存档瞬间 W 若已按住，不把它误判为“刚按下”。
		if (!ponderpp$initialized) {
			ponderpp$initialized = true;
			ponderpp$prevPonderDown = down;
			return;
		}

		if (PonderPpSettings.clickToOpen && down && !ponderpp$prevPonderDown) {
			ItemStack tracking = ponderpp$readStack(ponderpp$trackingField);
			ItemStack hovered = ponderpp$readStack(ponderpp$hoveredField);

			// hovered/tracking 均非空才说明当前帧确实悬浮着有 Ponder 场景的物品；
			// 当前在 Ponder 界面内时不重复打开。
			if (!tracking.isEmpty() && !hovered.isEmpty()
				&& !(Minecraft.getInstance().screen instanceof PonderUI)) {
				try {
					ScreenOpener.transitionTo(PonderUI.of(tracking));
					ponderpp$prevPonderDown = true;
					ci.cancel();
					return;
				} catch (Throwable ignored) {
					// 场景编译 / 资源注册并发等异常：本次不打开，退回原长按逻辑，避免崩溃。
				}
			}
		}
		ponderpp$prevPonderDown = down;
	}

	@Unique
	private static ItemStack ponderpp$readStack(Field f) {
		if (f == null)
			return ItemStack.EMPTY;
		try {
			Object o = f.get(null);
			return o instanceof ItemStack is ? is : ItemStack.EMPTY;
		} catch (Throwable t) {
			return ItemStack.EMPTY;
		}
	}
}
