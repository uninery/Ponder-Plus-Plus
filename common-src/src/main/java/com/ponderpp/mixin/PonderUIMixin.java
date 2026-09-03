package com.ponderpp.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ponderpp.PonderPpClient;
import com.ponderpp.PonderPpSettings;
import com.ponderpp.PonderUIExt;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;

/**
 * 共享逻辑注入：暂停冻结 + 逐帧事件判定 + 关闭清理 + 进度记忆。
 *
 * <p><b>进度记忆（需求 2）</b>：关闭思索时记录“当时所在的情景索引”和“该情景的进度时间”，
 * 再次打开同一思索时先切回该情景、再把进度跳回上次位置。整个流程分两阶段、每阶段只做一次：</p>
 * <ol>
 *   <li><b>切回情景</b>：等界面初始化稳定后，逐帧调用 Ponder 原生的 {@code scroll(true)}
 *       （旧的 fadeOut、新的 begin、lazyIndex 追到新 index，全部由 Ponder 自己维护），
 *       切到位后把 {@code lazyIndex} 直接钉到 {@code index}，避免切换过渡斜向平移残留。</li>
 *   <li><b>恢复进度</b>：仅对“当前活动情景”恢复。等 {@code totalTime} 累积到 ≥ 保存值后，
 *       调用 PonderUI 自己的 {@code seekToTime}（向前 seek 精确到位，内部自带向后时的 replay）。</li>
 * </ol>
 *
 * <p>为什么如此保守：{@link PonderScene#seekToTime} 只允许向前 seek（向后直接抛
 * {@code IllegalStateException}），且 {@code begin()}/{@code fadeOut()} 都会把
 * {@code currentTime}/{@code totalTime} 清零；对非活动情景 seek、或在总时长还没累积够时
 * seek，都会触发上述异常或被随后的 {@code begin()} 抹掉，表现为“进度条从头播放/多情景
 * 同时渲染/按钮失效”。因此这里刻意只恢复活动情景，且严格按 Ponder 自己的状态机推进。</p>
 *
 * <p>按钮的绘制交给各加载器自身的渲染钩子（Forge 用 {@code renderBackground}，
 * NeoForge 用 {@code renderWindow}），见各自模块的 Mixin。</p>
 */
@Mixin(PonderUI.class)
public abstract class PonderUIMixin {

	/** 本界面是否已完成“情景索引恢复”的初始化（每开一次思索只恢复一次）。 */
	@Unique
	private boolean ponderpp$indexRestored = false;

	/** 本界面是否已完成“进度恢复”（活动情景只 seek 一次）。 */
	@Unique
	private boolean ponderpp$progressRestored = false;

	/** 还需用原生 scroll(true) 前进的情景数（-1 表示未开始/已完成）。 */
	@Unique
	private int ponderpp$scrollRemaining = -1;

	/** 等界面稳定后再开始切情景的延迟计数（避免在首帧渲染前就滚动）。 */
	@Unique
	private int ponderpp$restoreWait = 3;

	/** 本界面是否已加载过配置（每开一次思索只读一次，避免逐帧重读磁盘）。 */
	@Unique
	private boolean ponderpp$settingsLoaded = false;

	@Inject(method = "tick", at = @At("HEAD"))
	private void ponderpp$onTick(CallbackInfo ci) {
		PonderUI self = (PonderUI) (Object) this;

		if (!ponderpp$settingsLoaded) {
			ponderpp$settingsLoaded = true;
			PonderPpSettings.load(); // 重读开关，改动 config 后重开思索即生效。
		}

		// 需求2：恢复上次的情景索引与进度。
		if (PonderPpSettings.rememberProgress)
			ponderpp$tryRestore(self);

		PonderUIExt.handleEvents(self);
	}

	@Unique
	private void ponderpp$tryRestore(PonderUI self) {
		try {
			ponderpp$driveRestoreIndex(self);
		} catch (Throwable ignored) {
		}

		// 只有在情景切换彻底完成后才恢复进度，避免被切换中的 begin() 抹掉。
		if (ponderpp$indexRestored && ponderpp$scrollRemaining == 0 && !ponderpp$progressRestored)
			ponderpp$restoreActiveScene(self);
	}

	/**
	 * 逐帧调用 Ponder 原生 {@code scroll(true)} 切回上次所在情景；切完后把 lazyIndex
	 * 钉到 index，让过渡动画立即结束（不再出现“旧情景斜着飞走、新情景从右上角进入”的
	 * 长时间双情景并列渲染）。
	 */
	@Unique
	private void ponderpp$driveRestoreIndex(PonderUI self) {
		int sceneCount = ponderpp$sceneCount(self);
		if (sceneCount <= 1) {
			// 单情景（或反射失败）无需滚动，但仍要允许后续恢复该情景的进度。
			ponderpp$indexRestored = true;
			ponderpp$scrollRemaining = 0;
			return;
		}

		if (!ponderpp$indexRestored) {
			if (ponderpp$restoreWait > 0) {
				ponderpp$restoreWait--;
				return;
			}
			ponderpp$indexRestored = true;

			int target = PonderPpSettings.getLastIndex();
			int cur = ponderpp$index(self);
			if (target <= 0 || target >= sceneCount || target <= cur) {
				ponderpp$scrollRemaining = 0;
				ponderpp$snapLazyIndex(self);
				return;
			}
			ponderpp$scrollRemaining = target - cur;
		}

		if (ponderpp$scrollRemaining > 0) {
			if (ponderpp$scroll(self, true))
				ponderpp$scrollRemaining--;
			else
				ponderpp$scrollRemaining = 0;
			if (ponderpp$scrollRemaining == 0)
				ponderpp$snapLazyIndex(self);
		}
	}

	/** 恢复“当前活动情景”的进度：等 totalTime 就绪后 seek 一次。 */
	@Unique
	private void ponderpp$restoreActiveScene(PonderUI self) {
		try {
			PonderScene sc = self.getActiveScene();
			if (sc == null)
				return;
			int saved = PonderPpSettings.getProgress(sc.getLocation().toString());
			if (saved <= 0) {
				ponderpp$progressRestored = true; // 无记录/记为0：从头播放，也视作完成。
				return;
			}
			if (sc.getTotalTime() < saved)
				return; // 该情景总时长尚未累积到保存位置，下帧再试。
			if (sc.getCurrentTime() >= saved) {
				ponderpp$progressRestored = true; // 已到达/越过目标，无需再动。
				ponderpp$maybeAutoPause(self);
				return;
			}
			// totalTime ≥ saved 且 currentTime < saved：向前 seek 一次到位。
			self.seekToTime(saved);
			ponderpp$progressRestored = true;
			ponderpp$maybeAutoPause(self);
		} catch (Throwable ignored) {
			// seek 异常（如极端边界）时放弃本次恢复，绝不阻塞正常播放。
			ponderpp$progressRestored = true;
		}
	}

	/** 需求3：跳转到上次位置后，按“自动暂停”开关决定是否立即暂停，方便从上次进度继续。 */
	@Unique
	private void ponderpp$maybeAutoPause(PonderUI self) {
		if (PonderPpSettings.autoPause)
			PonderPpClient.setPaused(self);
	}

	/** 暂停时拦截对 {@code PonderScene#tick()} 的调用，使场景时间不前进，其余 tick 照常。 */
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/ponder/foundation/PonderScene;tick()V"))
	private void ponderpp$holdSceneTick(PonderScene scene) {
		PonderUI self = (PonderUI) (Object) this;
		if (!PonderPpClient.isPaused(self))
			scene.tick();
	}

	@Inject(method = "removed", at = @At("TAIL"))
	private void ponderpp$onRemoved(CallbackInfo ci) {
		PonderUI self = (PonderUI) (Object) this;
		PonderPpClient.onScreenClosed(self);

		// 需求2：只记录“活动情景”的进度与所在索引（非活动情景已被 fadeOut 清零）。
		try {
			if (PonderPpSettings.rememberProgress) {
				PonderScene sc = self.getActiveScene();
				if (sc != null)
					PonderPpSettings.setProgress(sc.getLocation().toString(), sc.getCurrentTime());
				PonderPpSettings.setLastIndex(ponderpp$index(self));
			}
		} catch (Throwable ignored) {
		}
	}

	// ---- 反射工具（Ponder 私有成员，两版均同名字段/方法，失败时静默降级）----

	/** 把 lazyIndex 钉到当前 index，立即结束切换过渡。失败则忽略（过渡会自然收敛）。 */
	@Unique
	private static void ponderpp$snapLazyIndex(PonderUI self) {
		try {
			Field fIndex = PonderUI.class.getDeclaredField("index");
			Field fLazy = PonderUI.class.getDeclaredField("lazyIndex");
			fIndex.setAccessible(true);
			fLazy.setAccessible(true);
			Object lazy = fLazy.get(self);
			Method setValue = lazy.getClass().getMethod("startWithValue", double.class);
			setValue.invoke(lazy, (double) fIndex.getInt(self));
		} catch (Throwable ignored) {
		}
	}

	/** 反射读取当前情景索引（失败返回 0）。 */
	@Unique
	private static int ponderpp$index(PonderUI self) {
		try {
			Field f = PonderUI.class.getDeclaredField("index");
			f.setAccessible(true);
			return f.getInt(self);
		} catch (Throwable t) {
			return 0;
		}
	}

	/** 反射读取情景数量（失败返回 0，调用方会按 0 跳过滚动）。 */
	@Unique
	private static int ponderpp$sceneCount(PonderUI self) {
		try {
			Field f = PonderUI.class.getDeclaredField("scenes");
			f.setAccessible(true);
			Object list = f.get(self);
			return list == null ? 0 : ((java.util.List<?>) list).size();
		} catch (Throwable t) {
			return 0;
		}
	}

	/** 反射调用 PonderUI 原生 {@code scroll(boolean)}（protected），返回是否发生了切换。 */
	@Unique
	private static boolean ponderpp$scroll(PonderUI self, boolean forward) {
		try {
			Method m = PonderUI.class.getDeclaredMethod("scroll", boolean.class);
			m.setAccessible(true);
			return Boolean.TRUE.equals(m.invoke(self, forward));
		} catch (Throwable t) {
			return false;
		}
	}
}
