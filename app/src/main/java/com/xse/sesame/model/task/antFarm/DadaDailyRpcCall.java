package com.xse.sesame.model.task.antFarm;

import com.xse.sesame.hook.ApplicationHook;
import com.xse.sesame.util.StringUtil;

/**
 * @author Constanline
 * @since 2023/08/04
 */
public class DadaDailyRpcCall {
    
    public static String home(String activityId) {
        return ApplicationHook.requestString("com.alipay.reading.game.dadaDaily.home",
                "[{\"activityId\":" + activityId + ",\"dadaVersion\":\"1.3.0\",\"version\":1}]");
    }
    
    public static String submit(String activityId, String answer, Long questionId) {
        return ApplicationHook.requestString("com.alipay.reading.game.dadaDaily.submit",
                "[{\"activityId\":" + activityId + ",\"answer\":\"" + StringUtil.escapeJson(answer) + "\",\"dadaVersion\":\"1.3.0\"," +
                "\"questionId\":" + questionId + ",\"version\":1}]");
    }
}
