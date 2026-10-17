package com.serendisand.sfly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SFlyConstants {
    public static final String MOD_ID = "sfly";
    /** 根命令名，别再开别名 —— 多一棵树就多一份维护面。 */
    public static final String ROOT_COMMAND = "sf";
    public static final Logger LOGGER = LoggerFactory.getLogger("SFly");

    private SFlyConstants() {
    }
}
