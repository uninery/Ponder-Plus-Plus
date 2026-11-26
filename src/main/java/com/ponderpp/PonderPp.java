package com.ponderpp;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Ponder++ (思索++) — NeoForge 1.21.1 主入口。
 *
 * <p>本模组仅客户端生效：逻辑全部通过 Mixin 注入思索(Ponder)界面类实现，
 * 服务端无需额外逻辑，因此这里仅作 mod 装载与日志初始化。</p>
 */
@Mod(PonderPp.MOD_ID)
public class PonderPp {

	public static final String MOD_ID = "ponderpp";
	public static final Logger LOGGER = LogUtils.getLogger();

	public PonderPp(IEventBus modEventBus) {
		LOGGER.info("Ponder++ (思索++) loaded for NeoForge 1.21.1");
	}
}
