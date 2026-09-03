package com.ponderpp;

import net.createmod.ponder.foundation.ui.PonderUI;
import javax.annotation.Nullable;

/**
 * 全局暂停状态持有者。
 *
 * <p>由于同一个时刻最多只有一个思索(Ponder)界面打开，我们用一条对当前界面的
 * 强引用(切换界面时旧引用会被覆盖)来表示“哪个思考界面正被暂停”。当暂停指向的
 * 界面被关闭时，{@link #onScreenClosed} 会被调用并自动解除暂停态。</p>
 */
public final class PonderPpClient {
	private PonderPpClient() {}

	/** 当前被暂停的思索界面；{@code null} 表示没有界面被暂停。 */
	@Nullable
	private static PonderUI pausedScreen;

	/** 拖动进度条时是否处于拖动中（避免在拖动中被其它逻辑打断）。 */
	private static boolean dragging;

	/** 是否处于暂停：仅当当前界面是暂停目标时才成立。 */
	public static boolean isPaused(@Nullable PonderUI screen) {
		return screen != null && screen == pausedScreen;
	}

	/** 切换指定界面的暂停状态。 */
	public static void togglePause(PonderUI screen) {
		if (pausedScreen == screen) {
			pausedScreen = null;
		} else {
			pausedScreen = screen;
		}
	}

	/** 明确设置为暂停。 */
	public static void setPaused(PonderUI screen) {
		pausedScreen = screen;
	}

	/** 明确恢复播放。 */
	public static void setPlaying() {
		pausedScreen = null;
	}

	/** 当界面被关闭时调用，自动解除暂停态。 */
	public static void onScreenClosed(PonderUI screen) {
		if (pausedScreen == screen) {
			pausedScreen = null;
		}
	}

	public static boolean isDragging() {
		return dragging;
	}

	public static void setDragging(boolean value) {
		dragging = value;
	}
}
