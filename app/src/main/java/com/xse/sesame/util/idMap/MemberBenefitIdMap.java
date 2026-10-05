package com.xse.sesame.util.idMap;

import java.util.Collections;
import java.util.Map;

import com.xse.sesame.util.FileUtil;

/**
 * 会员积分权益缓存（双表结构）。
 * <p>{@code idMap}：benefitId -> 权益名（供 UI 列表勾选展示）；
 * {@code detailMap}：benefitId -> itemId|strategyType|point|yuan|grabHour（供兑换时直接取参数，避免重复查询详情）。
 */
public class MemberBenefitIdMap {

    private static final StringMapStore STORE = new StringMapStore(FileUtil::getMemberBenefitIdMapFile);
    private static final StringMapStore DETAIL_STORE = new StringMapStore(FileUtil::getMemberBenefitDetailMapFile);

    /** detailMap 分隔符，各字段不允许包含该字符 */
    private static final String SEP = "|";

    public static Map<String, String> getMap() {
        return STORE.getMap();
    }

    /** 只读视图：benefitId -> detail 串 */
    public static Map<String, String> getDetailMap() {
        return Collections.unmodifiableMap(DETAIL_STORE.getMap());
    }

    /**
     * 仅写入名称（兼容旧调用，详情不更新）。
     */
    public static void add(String key, String value) {
        STORE.add(key, value);
    }

    /**
     * 完整收录一条权益：名称入 idMap（已存在同名则保留旧名），详情入 detailMap。
     *
     * @param benefitId    权益ID
     * @param name         权益名称；为空时仅写详情
     * @param itemId       商品ID；为空表示未知
     * @param strategyType 支付策略（POINT_PAY / POINT_CASH_PAY）
     * @param point        所需积分；为空表示未知
     * @param yuan         现金价（元）；为空表示未知
     * @param grabHour     整点秒杀时刻（-1 表示非秒杀商品）
     */
    public static void addBenefitDetail(String benefitId, String name, String itemId, String strategyType, String point, String yuan, String grabHour) {
        addBenefitDetail(benefitId, name, itemId, strategyType, point, yuan, grabHour, "");
    }

    /**
     * 完整收录一条权益（含商品图片 URL）：名称入 idMap，详情入 detailMap。
     * <p>detailMap 格式：itemId|strategyType|point|yuan|grabHour|pic
     */
    public static void addBenefitDetail(String benefitId, String name, String itemId, String strategyType, String point, String yuan, String grabHour, String pic) {
        if (benefitId == null || benefitId.isEmpty()) {
            return;
        }
        if (name != null && !name.isEmpty()) {
            String oldName = STORE.get(benefitId);
            if (oldName == null || oldName.isEmpty()) {
                STORE.add(benefitId, name);
            }
        }
        DETAIL_STORE.add(benefitId, safe(itemId) + SEP + safe(strategyType) + SEP + safe(point) + SEP + safe(yuan) + SEP + safe(grabHour) + SEP + safe(pic));
    }

    public static void remove(String key) {
        STORE.remove(key);
        DETAIL_STORE.remove(key);
    }

    /** 兼容旧调用：名称与详情一起载入。 */
    public static void load(String userId) {
        STORE.load(userId);
        DETAIL_STORE.load(userId);
    }

    /** 兼容旧调用：名称与详情一起写回。 */
    public static boolean save(String userId) {
        boolean a = STORE.save(userId);
        boolean b = DETAIL_STORE.save(userId);
        return a && b;
    }

    public static void clear() {
        STORE.clear();
        DETAIL_STORE.clear();
    }

    /* ==================== 查询辅助 ==================== */

    /** 权益ID -> 权益名；未收录返回 null */
    public static String getRealName(String benefitId) {
        if (benefitId == null) {
            return null;
        }
        return STORE.get(benefitId);
    }

    /** 权益名 -> 权益ID（精确匹配）；未收录返回 null */
    public static String getBenefitId(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> e : STORE.getMap().entrySet()) {
            if (name.equals(e.getValue())) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 关键词模糊匹配第一个权益名，返回 benefitId；未命中返回 null */
    public static String searchBenefitByKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : STORE.getMap().entrySet()) {
            String v = e.getValue();
            if (v != null && v.contains(keyword)) {
                return e.getKey();
            }
        }
        return null;
    }

    public static String getItemId(String benefitId) {
        return detailPart(benefitId, 0);
    }

    public static String getStrategyType(String benefitId) {
        return detailPart(benefitId, 1);
    }

    public static String getPoint(String benefitId) {
        return detailPart(benefitId, 2);
    }

    public static String getYuan(String benefitId) {
        return detailPart(benefitId, 3);
    }

    public static String getGrabHour(String benefitId) {
        return detailPart(benefitId, 4);
    }

    /** 商品图片 URL（可能为空） */
    public static String getPic(String benefitId) {
        return detailPart(benefitId, 5);
    }

    private static String detailPart(String benefitId, int index) {
        if (benefitId == null) {
            return "";
        }
        String detail = DETAIL_STORE.get(benefitId);
        if (detail == null || detail.isEmpty()) {
            return "";
        }
        String[] parts = detail.split("\\|", -1);
        if (index >= parts.length) {
            return "";
        }
        return parts[index];
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace(SEP, "");
    }

}