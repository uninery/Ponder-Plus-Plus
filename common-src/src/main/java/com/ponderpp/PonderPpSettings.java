package com.ponderpp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;

/**
 * ponderpp 的简单配置与进度记忆。
 *
 * <p>配置文件都放在 {@code <游戏目录>/config/ponderpp/} 文件夹下（UTF-8）：
 * <ul>
 *   <li><b>{@code settings.properties}</b> —— 功能开关：
 *     <ul>
 *       <li>{@code clickToOpen=true} —— 单击 W 直接打开思索（替代长按）。</li>
 *       <li>{@code rememberProgress=true} —— 关闭思索时记录当前进度，再次打开同一场景时自动跳回。</li>
 *       <li>{@code autoPause=true} —— 重开跳转到上次位置后自动暂停。</li>
 *     </ul>
 *     关闭对应项写 {@code false} 或删除该行即可。开关在每次打开思索时重新读取，改完重进即生效。
 *   </li>
 *   <li><b>{@code progress.properties}</b> —— 进度缓存（按场景自动记录，无需手动编辑）：
 *     <ul>
 *       <li>{@code progress.<scene-location>=时间} —— 各场景上次关闭时的进度。</li>
 *       <li>{@code ponderpp.lastIndex=索引} —— 上次关闭时所在的场景索引。</li>
 *     </ul>
 *   </li>
 * </ul>
 * 两个文件用 {@link Properties#store} 写入、{@link Properties#load} 读回，
 * 保证 key 中的 {@code :} 等字符被正确转义（否则会导致进度 key 被截断而“丢失”）。
 */
public final class PonderPpSettings {

	private PonderPpSettings() {}

	/** 单击 W 打开思索。 */
	public static boolean clickToOpen = true;
	/** 关闭时记住进度，重开自动跳转。 */
	public static boolean rememberProgress = true;
	/** 跳转到上次位置后自动暂停（便于从上次进度处继续）。 */
	public static boolean autoPause = true;


	/** 功能开关。 */
	private static final Properties settingsProps = new Properties();
	/** 进度缓存。 */
	private static final Properties progressProps = new Properties();

	/** 配置文件夹：{@code config/ponderpp}。 */
	private static Path configDir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config/ponderpp");
	}

	/** 功能开关文件：{@code config/ponderpp/settings.properties}。 */
	private static Path settingsPath() {
		return configDir().resolve("settings.properties");
	}

	/** 进度缓存文件：{@code config/ponderpp/progress.properties}。 */
	private static Path progressPath() {
		return configDir().resolve("progress.properties");
	}

	/** 重读配置文件（幂等安全；每次打开思索时调用以确保改动即时生效）。 */
	public static void load() {
		settingsProps.clear();
		progressProps.clear();
		clickToOpen = true;
		rememberProgress = true;
		autoPause = true;
		try {
			Files.createDirectories(configDir());

			Path sp = settingsPath();
			if (Files.exists(sp)) {
				try (Reader r = Files.newBufferedReader(sp, StandardCharsets.UTF_8)) {
					settingsProps.load(r);
				}
			}

			Path pp = progressPath();
			if (Files.exists(pp)) {
				try (Reader r = Files.newBufferedReader(pp, StandardCharsets.UTF_8)) {
					progressProps.load(r);
				}
			}

			clickToOpen = parseBool(settingsProps.getProperty("clickToOpen"), true);
			rememberProgress = parseBool(settingsProps.getProperty("rememberProgress"), true);
			autoPause = parseBool(settingsProps.getProperty("autoPause"), true);
			if (!Files.exists(sp))
				saveSettings(); // 首次生成默认开关文件，便于用户在 config 目录里查看/修改。
		} catch (IOException ignored) {
			// 读失败的极端情况保持默认值，不影响游戏。
		}
	}

	/**
	 * 记录某场景进度。t=0 也会写入（表示“从头开始”），否则玩家重开→回退到 0→再退出时，
	 * 旧的非 0 快照会残留，导致下次重开跳回旧位置而不是 0。
	 */
	public static void setProgress(String sceneLocation, int time) {
		if (!rememberProgress)
			return;
		progressProps.setProperty("progress." + sceneLocation, String.valueOf(Math.max(0, time)));
		saveProgress();
	}

	/** 读取某场景之前记录的进度；无记录返回 0。 */
	public static int getProgress(String sceneLocation) {
		try {
			return Integer.parseInt(progressProps.getProperty("progress." + sceneLocation, "0"));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	/** 记录上次关闭思索时所在的场景索引（多场景场景切换用）。 */
	public static void setLastIndex(int index) {
		progressProps.setProperty("ponderpp.lastIndex", String.valueOf(index));
		saveProgress();
	}

	/** 读取上次关闭时的场景索引；无则返回 0。 */
	public static int getLastIndex() {
		try {
			return Integer.parseInt(progressProps.getProperty("ponderpp.lastIndex", "0"));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	/** 保存功能开关文件（界面按钮切换开关后调用）。 */
	public static void save() {
		// 先把当前字段值同步回 settingsProps，界面按钮切换后能正确持久化。
		settingsProps.setProperty("clickToOpen", String.valueOf(clickToOpen));
		settingsProps.setProperty("rememberProgress", String.valueOf(rememberProgress));
		settingsProps.setProperty("autoPause", String.valueOf(autoPause));
		saveSettings();
	}

	private static void saveSettings() {
		try {
			Path p = settingsPath();
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				settingsProps.store(w, "ponderpp function switches");
			}
		} catch (IOException ignored) {
		}
	}

	private static void saveProgress() {
		try {
			Path p = progressPath();
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				progressProps.store(w, "ponderpp progress cache");
			}
		} catch (IOException ignored) {
		}
	}

	private static boolean parseBool(String value, boolean dflt) {
		if (value == null)
			return dflt;
		return value.equalsIgnoreCase("true");
	}
}
