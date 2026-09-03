package com.ponderpp;

import org.lwjgl.glfw.GLFW;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 版本无关的工具类：把“暂停/播放把交互与渲染”集中在普通静态方法里，
 * 供各加载器的 Mixin 调用（各平台只管用自身可注入的渲染钩子触发 renderButton）。
 */
public final class PonderUIExt {

	private PonderUIExt() {}

	/** 暂停/播放按钮贴图：ponder_buttons.png（64x32）。 */
	public static final ResourceLocation TEX =
		ResourceLocation.tryParse("ponderpp:textures/gui/ponder_buttons.png");

	private static boolean prevLeft = false;
	private static boolean prevSpace = false;

	/** 逐帧事件：暂停按钮点击、空格切换、进度条点击/拖动 seek。 */
	public static void handleEvents(PonderUI self) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != self)
			return;

		int guiW = mc.getWindow().getGuiScaledWidth();
		int guiH = mc.getWindow().getGuiScaledHeight();
		double mouseX = mc.mouseHandler.xpos() * guiW / (double) mc.getWindow().getScreenWidth();
		double mouseY = mc.mouseHandler.ypos() * guiH / (double) mc.getWindow().getScreenHeight();

		boolean leftDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
			== GLFW.GLFW_PRESS;
		boolean spaceDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;

		// 左键“刚按下”的边沿：命中暂停或功能开关则切换对应状态。
		if (leftDown && !prevLeft) {
			int[] btn = buttonRect(self);
			if (hitRect(btn, mouseX, mouseY)) {
				PonderPpClient.togglePause(self);
			} else if (hitRect(settingsRect1(self), mouseX, mouseY)) {
				PonderPpSettings.clickToOpen = !PonderPpSettings.clickToOpen;
				PonderPpSettings.save();
			} else if (hitRect(settingsRect2(self), mouseX, mouseY)) {
				PonderPpSettings.rememberProgress = !PonderPpSettings.rememberProgress;
				PonderPpSettings.save();
			} else if (hitRect(settingsRect3(self), mouseX, mouseY)) {
				PonderPpSettings.autoPause = !PonderPpSettings.autoPause;
				PonderPpSettings.save();
			}
		}

		// 空格键“刚按下”边沿：切换暂停。
		if (spaceDown && !prevSpace) {
			PonderPpClient.togglePause(self);
		}

		prevLeft = leftDown;
		prevSpace = spaceDown;

		// 进度条：按住左键且鼠标命中进度条区域 → 换算任意时间并 seek（点击与拖动共用）。
		if (leftDown && inBarRegion(self, mouseX, mouseY)) {
			seekFromX(self, mouseX);
		}
	}

	/** 绘制暂停/播放切换按钮（贴图渲染）。 */
	public static void renderButton(PonderUI self, GuiGraphics graphics) {
		int[] btn = buttonRect(self);
		int bx = btn[0];
		int by = btn[1];
		int bw = btn[2];
		int bh = btn[3];

		boolean paused = PonderPpClient.isPaused(self);
		int bg = paused ? 0xCC_2e5f3a : 0xCC_3a3a55;
		int border = paused ? 0xFF_6fbf7f : 0xFF_8f8fd0;
		graphics.fill(bx, by, bx + bw, by + bh, bg);
		graphics.fill(bx, by, bx + bw, by + 1, border);
		graphics.fill(bx, by, bx + 1, by + bh, border);
		graphics.fill(bx + bw - 1, by, bx + bw, by + bh, border);
		graphics.fill(bx, by + bh - 1, bx + bw, by + bh, border);

		// 贴图 64x32：左半(播放) u=6，右半(暂停) u=38；两者 20x20 内容区。
		if (paused) {
			graphics.blit(TEX, bx, by, 38f, 6f, 20, 20, 64, 32);
		} else {
			graphics.blit(TEX, bx, by, 6f, 6f, 20, 20, 64, 32);
		}

		// 功能开关（右上角按钮下方）：自动暂停 / 单击W / 记进度。
		renderToggle(graphics, settingsRect3(self), "ponderpp.toggle.autoPause", PonderPpSettings.autoPause);
		renderToggle(graphics, settingsRect1(self), "ponderpp.toggle.clickToOpen", PonderPpSettings.clickToOpen);
		renderToggle(graphics, settingsRect2(self), "ponderpp.toggle.rememberProgress", PonderPpSettings.rememberProgress);
	}

	private static void renderToggle(GuiGraphics graphics, int[] r, String translationKey, boolean on) {
		graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? 0xCC_2e5f3a : 0xCC_555555);
		graphics.fill(r[0], r[1], r[0] + r[2], r[1] + 1, 0xFF_ffffff);
		Component text = Component.translatable(translationKey)
			.append(Component.literal(" "))
			.append(Component.translatable(on ? "ponderpp.toggle.on" : "ponderpp.toggle.off"));
		graphics.drawString(Minecraft.getInstance().font, text, r[0] + 3, r[1] + 2, 0xFFFFFF);
	}

	public static boolean inBarRegion(PonderUI self, double mouseX, double mouseY) {
		double left = (double) barLeftX(self) - 2;
		double right = left + barWidth(self) + 6;
		double top = (double) barY(self) - 3;
		double bottom = (double) barY(self) + 21;
		return mouseX >= left && mouseX < right && mouseY >= top && mouseY <= bottom;
	}

	public static void seekFromX(PonderUI self, double mouseX) {
		PonderScene scene = self.getActiveScene();
		if (scene == null)
			return;
		int totalTime = scene.getTotalTime();
		if (totalTime <= 0)
			return;
		double ratio = (mouseX - barLeftX(self)) / (barWidth(self) + 4);
		int time = (int) Math.floor(Mth.clamp(ratio, 0.0, 1.0) * totalTime);
		self.seekToTime(time);
	}

	/** 进度条区域（与 {@code PonderUI.init()} 中一致）。 */
	public static double barWidth(PonderUI self) {
		return 220;
	}

	public static int barLeftX(PonderUI self) {
		return self.width / 2 - 110;
	}

	public static int barY(PonderUI self) {
		return self.height - 27;
	}

	/** 暂停按钮矩形 {x, y, w, h}，置于进度条右端之后。 */
	public static int[] buttonRect(PonderUI self) {
		int bx = barLeftX(self) + (int) barWidth(self) + 8;
		int by = barY(self) - 10;
		return new int[] { bx, by, 20, 20 };
	}

	/** 功能开关“单击W”矩形，位于暂停按钮上方一行。 */
	public static int[] settingsRect1(PonderUI self) {
		int bx = barLeftX(self) + (int) barWidth(self) + 8;
		return new int[] { bx, barY(self) - 36, 80, 11 };
	}

	/** 功能开关“记进度”矩形，位于“单击W”开关上方。 */
	public static int[] settingsRect2(PonderUI self) {
		int bx = barLeftX(self) + (int) barWidth(self) + 8;
		return new int[] { bx, barY(self) - 23, 80, 11 };
	}

	/** 功能开关“自动暂停”矩形，位于“单击W”开关上方。 */
	public static int[] settingsRect3(PonderUI self) {
		int bx = barLeftX(self) + (int) barWidth(self) + 8;
		return new int[] { bx, barY(self) - 49, 80, 11 };
	}

	/** 鼠标是否命中某矩形 {x,y,w,h}。 */
	public static boolean hitRect(int[] r, double mx, double my) {
		return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
	}
}
