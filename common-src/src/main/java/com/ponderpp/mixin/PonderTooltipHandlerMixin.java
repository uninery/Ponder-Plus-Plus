package com.ponderpp.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;
import com.ponderpp.PonderPpSettings;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.enums.PonderKeybinds;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.KeyMapping;
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
 * <p><b>Ponder 版本兼容</b>：{@code PonderKeybinds#isDown()} 只在 Ponder 1.0.92+ 存在
 * （1.0.81 没有，直接调用会 {@code NoSuchMethodError} 崩游戏）。因此这里全部走反射：
 * 有 {@code isDown()} 就用它；没有就取 {@code getKeybind()} / 私有字段 {@code keyMapping}
 * 拿到 {@link KeyMapping}，再用 {@link InputConstants#isKeyDown(long, int)} 自己轮询按键，
 * 语义与新版 {@code isDown()} 一致。再加上外层 try/catch，任何版本差异都不会崩游戏。</p>
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

	@Unique
	private static Field ponderpp$trackingField;
	@Unique
	private static Field ponderpp$hoveredField;

	/** Ponder 1.0.92+ 的 {@code PonderKeybinds#isDown()}；1.0.81 没有这个方法。 */
	@Unique
	private static Method ponderpp$isDownMethod;

	/** Ponder 1.0.81 的 {@code PonderKeybinds#getKeybind()}；1.0.92 没有这个方法。 */
	@Unique
	private static Method ponderpp$getKeybindMethod;

	/** 两个版本都有的私有字段 {@code keyMapping}（Ponder 注册键位后才有值）。 */
	@Unique
	private static Field ponderpp$keyMappingField;

	/** 已解析到的 Ponder 键位；只缓存成功结果（注册前解析失败会下一帧重试）。 */
	@Unique
	private static KeyMapping ponderpp$cachedKeybind;

	static {
		ponderpp$trackingField = ponderpp$findField(PonderTooltipHandler.class, "trackingStack");
		ponderpp$hoveredField = ponderpp$findField(PonderTooltipHandler.class, "hoveredStack");
		ponderpp$isDownMethod = ponderpp$findMethod(PonderKeybinds.class, "isDown");
		ponderpp$getKeybindMethod = ponderpp$findMethod(PonderKeybinds.class, "getKeybind");
		ponderpp$keyMappingField = ponderpp$findField(PonderKeybinds.class, "keyMapping");
	}

	@Unique
	private static Field ponderpp$findField(Class<?> owner, String name) {
		try {
			Field f = owner.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable ignored) {
			// 版本差异导致取不到字段时，仅退回长按行为，不影响其它功能。
			return null;
		}
	}

	@Unique
	private static Method ponderpp$findMethod(Class<?> owner, String name) {
		try {
			Method m = owner.getDeclaredMethod(name);
			m.setAccessible(true);
			return m;
		} catch (Throwable ignored) {
			// 当前 Ponder 版本没有该方法（或不可访问）：改走其它兼容路径。
			return null;
		}
	}

	@Inject(method = "deferredTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void ponderpp$onDeferredTick(CallbackInfo ci) {
		try {
			ponderpp$handleDeferredTick(ci);
		} catch (Throwable ignored) {
			// 任何 Ponder 版本差异（例如 1.0.81 没有 isDown()）都不得让游戏崩溃：
			// 本帧按“未按下”处理，下帧继续。
		}
	}

	@Unique
	private static void ponderpp$handleDeferredTick(CallbackInfo ci) {
		boolean down = ponderpp$isPonderDown();

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

	/**
	 * 判断 PONDER 键当前是否按下：优先用 Ponder 自带的判定，缺失时自己轮询实际键位。
	 *
	 * <p>不能直接写 {@code PonderKeybinds.PONDER.isDown()}：该 API 在 1.0.81 中不存在，
	 * 而玩家实例里的 Ponder 版本由整合包决定，编译期版本（1.0.92）与运行期不一定一致。</p>
	 */
	@Unique
	private static boolean ponderpp$isPonderDown() {
		if (ponderpp$isDownMethod != null) {
			try {
				Object down = ponderpp$isDownMethod.invoke(PonderKeybinds.PONDER);
				if (down instanceof Boolean b)
					return b;
			} catch (Throwable ignored) {
				// 反射失败则走下面的键位轮询。
			}
		}

		KeyMapping mapping = ponderpp$resolveKeybind();
		if (mapping != null) {
			try {
				InputConstants.Key key = mapping.getKey();
				if (key != null && key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0) {
					Minecraft minecraft = Minecraft.getInstance();
					if (minecraft != null && minecraft.getWindow() != null)
						return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
				}
			} catch (Throwable ignored) {
				// 未绑定 / 窗口未就绪：当作未按下。
			}
		}
		return false;
	}

	/** 从 1.0.81 的 {@code getKeybind()} 或私有字段 {@code keyMapping} 取键位对象。 */
	@Unique
	private static KeyMapping ponderpp$resolveKeybind() {
		if (ponderpp$cachedKeybind != null)
			return ponderpp$cachedKeybind;
		try {
			Object keybind = ponderpp$getKeybindMethod != null
				? ponderpp$getKeybindMethod.invoke(PonderKeybinds.PONDER)
				: null;
			if (keybind == null && ponderpp$keyMappingField != null)
				keybind = ponderpp$keyMappingField.get(PonderKeybinds.PONDER);
			if (keybind instanceof KeyMapping mapping) {
				ponderpp$cachedKeybind = mapping;
				return mapping;
			}
		} catch (Throwable ignored) {
			// 键位尚未注册（客户端初始化早期）或当前版本无此 API。
		}
		return null;
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
