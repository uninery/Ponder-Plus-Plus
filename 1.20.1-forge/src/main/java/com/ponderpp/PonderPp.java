package com.ponderpp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Ponder++ (思索++) — Forge 1.20.1 主入口。
 *
 * <p>本模组仅客户端生效：逻辑全部通过 Mixin 注入思索(Ponder)界面类实现。</p>
 */
@Mod(PonderPp.MOD_ID)
public class PonderPp {

	public static final String MOD_ID = "ponderpp";
	public static final Logger LOGGER = LogManager.getLogger();

	public PonderPp() {
		LOGGER.info("Ponder++ (思索++) loaded for Forge 1.20.1 (client={})", FMLEnvironment.dist);
	}
}
