package com.github.balloonupdate;

import mcpatch.McPatchClient;

public class BalloonUpdateMain
{
    /**
     * 从ModLoader启动
     * @return 是否有文件更新，如果有返回true。其它情况返回false
     */
    public static boolean modloader(boolean enableLogFile, boolean disableTheme)
    {
        return McPatchClient.modloader(enableLogFile, disableTheme);
    }
}
