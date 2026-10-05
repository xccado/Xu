package com.xse.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;

import com.xse.sesame.data.task.ModelTask;
import com.xse.sesame.hook.ApplicationHook;
import com.xse.sesame.util.Log;
import com.xse.sesame.util.MessageUtil;
import com.xse.sesame.util.Status;
import com.xse.sesame.util.TimeUtil;
import com.xse.sesame.util.idMap.MemberBenefitIdMap;
import com.xse.sesame.util.idMap.UserIdMap;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AntMemberExchange {

    private static final String TAG = AntMemberExchange.class.getSimpleName();

    private static final int LOWER_POINT = 0;
    private static final int UPPER_POINT = 99999999;

    /* ==================== 动态全量拉库 ==================== */

    /** 天天兑宝贝专区（硬编码 deliveryId） */
    private static final String[] TTZ_DELIVERY_IDS = {
            "94000SR2025092914966006", "94000SR2025091714812006", "94000SR2025103015307004",
            "94000SR2025120515776001", "94000SR2025120515776002", "94000SR2025120515775004"
    };
    /** 热门商品硬编码 entityId（补全 queryShandieEntityList） */
    private static final String[] HOT_ENTITY_IDS = {
            "202510110476374689", "202411271113950678", "202503282068512481", "202506300100792704",
            "202509221475253538", "202511031493192643", "202504011766076720", "202511280684183793",
            "202504011912099770", "202504011027283348", "202511201305001884", "202511250514181097",
            "202509090310811271", "202510111659672001", "202511250394607737", "202503280143371402",
            "202512151094741302", "202511272065690214", "202511240789338173", "202512011252766616",
            "202402040038601137", "AMS2025010930059205", "AMS2024102534952789", "202412101362244986",
            "202508191632628208", "202508191118960837", "202508190493666634", "202508190199255438",
            "202508191995966892", "202508191969981774", "202510101997752285", "202510100708029984",
            "202510101512061411", "202510240428358329", "202509291033091128", "202407020171144796",
            "202509080777177741", "202510151693220726", "202503170091439407", "202509011173580856"
    };
    /** 详情补全硬编码 benefitId（querySingleBenefitDetail） */
    private static final String[] HOT_BENEFIT_IDS = {
            "202605131525470415", "202603091925734720", "202411271113950678", "202602040346754807",
            "202506300100792704", "202608190575855482", "202605190253221474", "202605140483303782"
    };
    /** 搜索频道补充关键词（searchMemberBenefit） */
    private static final String[] SEARCH_KEYWORDS = {
            "马克杯", "保温杯", "抽纸", "洗衣液", "洗洁精", "沐浴露",
            "洗发水", "数据线", "充电宝", "耳机", "雨伞", "咖啡", "零食", "视频会员"
    };

    /**
     * 动态全量拉取权益库并收录（幂等）。
     * 流程：导航分类 → 各专区/闪蝶 → 积分区间流 → 全积分兜底流 →
     * 天天兑宝贝专区 → 热门商品 → 详情补全 → 搜索频道补充。
     */
    public static void fetchDynamicBenefits() {
        try {
            String userId = UserIdMap.getCurrentUid();
            Set<String> crawled = new LinkedHashSet<>();

            // 1) 导航分类：逐个爬取专区与闪蝶（deliveryIdList 是数组，非单值）
            JSONObject naviJo = new JSONObject(AntMemberRpcCall.queryNavigationWithFilter());
            JSONArray naviInfoList = naviJo.optJSONObject("data") != null
                    ? naviJo.getJSONObject("data").optJSONArray("naviInfoList")
                    : naviJo.optJSONArray("naviInfoList");
            if (naviInfoList != null && naviInfoList.length() > 0) {
                for (int i = 0; i < naviInfoList.length(); i++) {
                    JSONObject navi = naviInfoList.getJSONObject(i);
                    if (navi == null) {
                        continue;
                    }
                    String naviCode = navi.optString("naviCode", "");
                    JSONArray deliveryIdList = navi.optJSONArray("deliveryIdList");
                    JSONArray customFilters = navi.optJSONObject("filterConfig") != null
                            ? navi.getJSONObject("filterConfig").optJSONArray("customFilters") : null;

                    // 1.1 专区 + 闪蝶
                    if (deliveryIdList != null) {
                        for (int d = 0; d < deliveryIdList.length(); d++) {
                            String deliveryId = deliveryIdList.optString(d);
                            if (deliveryId.isEmpty() || !crawled.add(deliveryId)) {
                                continue;
                            }
                            int zoneAdded = 0;
                            try {
                                JSONObject zoneJo = new JSONObject(AntMemberRpcCall.queryDeliveryZoneDetail(deliveryId));
                                JSONArray briefs = zoneJo.optJSONArray("briefConfigInfos");
                                if (briefs != null) {
                                    int before = MemberBenefitIdMap.getMap().size();
                                    for (int b = 0; b < briefs.length(); b++) {
                                        parseAndAddBenefitObject(briefs.getJSONObject(b));
                                    }
                                    zoneAdded = MemberBenefitIdMap.getMap().size() - before;
                                }
                            } catch (Throwable t) {
                                Log.err(TAG, "专区详情 err deliveryId=" + deliveryId, t);
                            }
                            int shandieAdded = crawlShandieZoneByDeliveryId(deliveryId, 5);
                            Log.i(TAG, "专区 deliveryId=" + deliveryId + " 收录 " + (zoneAdded + shandieAdded) + " 件");
                            TimeUtil.sleep(150L);
                        }
                    }

                    // 1.2 积分区间流（customFilters）
                    if (customFilters != null) {
                        for (int c = 0; c < customFilters.length(); c++) {
                            JSONObject filter = customFilters.optJSONObject(c);
                            if (filter == null) {
                                continue;
                            }
                            int pointMin = filter.optInt("pointMin", 0);
                            int pointMax = filter.optInt("pointMax", 0);
                            if (pointMax <= 0) {
                                pointMax = 99999999;
                            }
                            try {
                                JSONObject jo = new JSONObject(AntMemberRpcCall.queryIndexNaviBenefitFlowV2(
                                        naviCode, true, pointMin, pointMax, 1, 30));
                                JSONArray list = jo.optJSONArray("entityInfoList");
                                if (list != null) {
                                    for (int e = 0; e < list.length(); e++) {
                                        JSONObject entity = list.optJSONObject(e);
                                        if (entity != null) {
                                            parseAndAddBenefitObject(entity);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                Log.err(TAG, "积分区间流 err", t);
                            }
                            TimeUtil.sleep(150L);
                        }
                    }

                    // 1.3 各分类全积分流（前 2 页）
                    for (int page = 1; page <= 2; page++) {
                        try {
                            JSONObject jo = new JSONObject(AntMemberRpcCall.queryIndexNaviBenefitFlowV2(
                                    naviCode, false, 0, 99999999, page, 30));
                            JSONArray list = jo.optJSONArray("entityInfoList");
                            if (list != null) {
                                for (int e = 0; e < list.length(); e++) {
                                    JSONObject entity = list.optJSONObject(e);
                                    if (entity != null) {
                                        parseAndAddBenefitObject(entity);
                                    }
                                }
                            }
                            if (jo.optInt("nextPageNum", 0) == 0) {
                                break;
                            }
                        } catch (Throwable t) {
                            Log.err(TAG, "分类全积分流 err page=" + page, t);
                        }
                        TimeUtil.sleep(150L);
                    }
                }
            } else {
                // 1') 兜底流（无导航时，翻页至无下一页，上限 5 页）
                int pageNum = 1;
                while (pageNum > 0 && pageNum <= 5) {
                    JSONObject jo = new JSONObject(AntMemberRpcCall.queryIndexNaviBenefitFlowV2(pageNum, 30));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        Log.i(TAG, "兜底流 page=" + pageNum + " 失败 " + jo.optString("desc"));
                        break;
                    }
                    parseAndAddBenefit(jo.toString());
                    int next = jo.optInt("nextPageNum", 0);
                    int adNext = jo.optInt("nextAdPageNum", 0);
                    if (next > pageNum) {
                        pageNum = next;
                    } else if (adNext > pageNum) {
                        pageNum = adNext;
                    } else {
                        break;
                    }
                }
            }

            // 2) 天天兑宝贝专区（硬编码 deliveryId）
            for (String deliveryId : TTZ_DELIVERY_IDS) {
                try {
                    int added = crawlShandieZoneByDeliveryId(deliveryId, 5);
                    if (added > 0) {
                        Log.i(TAG, "天天兑宝贝专区 " + deliveryId + " 收录 " + added + " 件");
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "天天兑专区 err " + deliveryId, t);
                }
                TimeUtil.sleep(120L);
            }

            // 3) 热门商品（硬编码 entityId）
            try {
                List<String> entityIds = new ArrayList<>(HOT_ENTITY_IDS.length);
                Collections.addAll(entityIds, HOT_ENTITY_IDS);
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryShandieEntityList(null, entityIds, null, 1, 50));
                if (jo.optBoolean("success", false)) {
                    JSONArray benefits = jo.optJSONArray("benefits");
                    if (benefits != null) {
                        for (int i = 0; i < benefits.length(); i++) {
                            parseAndAddBenefitObject(benefits.getJSONObject(i));
                        }
                    }
                }
            } catch (Throwable t) {
                Log.err(TAG, "热门商品 err", t);
            }

            // 4) 详情补全（硬编码 benefitId）
            for (String benefitId : HOT_BENEFIT_IDS) {
                try {
                    JSONObject jo = new JSONObject(AntMemberRpcCall.querySingleBenefitDetail(benefitId));
                    if (jo.optBoolean("success", false)) {
                        JSONObject detail = jo.optJSONObject("benefitDetail");
                        if (detail != null) {
                            parseAndAddBenefitObject(detail);
                        }
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "详情补全 err " + benefitId, t);
                }
                TimeUtil.sleep(100L);
            }

            // 5) 详情补全：仅补 detail 缺失的权益（避免重复请求）
            int patched = 0;
            for (String benefitId : MemberBenefitIdMap.getMap().keySet()) {
                if (!MemberBenefitIdMap.getItemId(benefitId).isEmpty()) {
                    continue;
                }
                parseAndAddBenefit(AntMemberRpcCall.querySingleBenefitDetail(benefitId));
                patched++;
                if (patched > 50) {
                    break;
                }
            }

            // 6) 搜索频道补充（硬编码关键词）
            int searchAdded = 0;
            for (String keyword : SEARCH_KEYWORDS) {
                try {
                    JSONObject jo = new JSONObject(AntMemberRpcCall.searchMemberBenefit(keyword, 1, 20));
                    JSONArray list = jo.optJSONArray("entityInfoList");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject entity = list.optJSONObject(i);
                            if (entity != null) {
                                JSONObject benefitInfo = entity.optJSONObject("benefitInfo");
                                if (benefitInfo != null) {
                                    int before = MemberBenefitIdMap.getMap().size();
                                    parseAndAddBenefitObject(benefitInfo);
                                    searchAdded += MemberBenefitIdMap.getMap().size() - before;
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "搜索补充 err " + keyword, t);
                }
                TimeUtil.sleep(120L);
            }
            if (searchAdded > 0) {
                Log.i(TAG, "搜索频道补充收录 " + searchAdded + " 件");
            }

            MemberBenefitIdMap.save(userId);
            Log.i(TAG, "拉库完成，累计权益 " + MemberBenefitIdMap.getMap().size());
        } catch (Throwable t) {
            Log.err(TAG, "fetchDynamicBenefits err:", t);
        }
    }

    /**
     * 爬取单个专区/闪蝶分类：queryShandieEntityList 翻页拉取（每页 50 条），不足一页即止。
     *
     * @return 新增收录数量
     */
    public static int crawlShandieZoneByDeliveryId(String deliveryId, int pages) {
        int added = 0;
        try {
            for (int page = 1; page <= pages; page++) {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryShandieEntityList(
                        Collections.singletonList(deliveryId), null, null, page, 50));
                if (!jo.optBoolean("success", false)) {
                    break;
                }
                JSONArray benefits = jo.optJSONArray("benefits");
                if (benefits == null || benefits.length() == 0) {
                    break;
                }
                int before = MemberBenefitIdMap.getMap().size();
                for (int i = 0; i < benefits.length(); i++) {
                    parseAndAddBenefitObject(benefits.getJSONObject(i));
                }
                added += MemberBenefitIdMap.getMap().size() - before;
                if (benefits.length() < 50) {
                    break;
                }
                TimeUtil.sleep(120L);
            }
        } catch (Throwable t) {
            Log.err(TAG, "crawlShandieZoneByDeliveryId err:", t);
        }
        return added;
    }

    /**
     * 解析权益列表 JSON 并收录（递归收集 benefitInfo 节点）。
     * 名称 + 详情（itemId/strategyType/point/yuan/grabHour/pic）双表收录。
     */
    public static void parseAndAddBenefit(String raw) {
        try {
            if (raw == null || raw.isEmpty()) {
                return;
            }
            JSONObject root = new JSONObject(raw);
            if (!MessageUtil.checkResultCode(TAG, root)) {
                return;
            }
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                collectBenefitFromObject(data);
            }
            collectBenefitFromObject(root);
        } catch (Throwable t) {
            Log.err(TAG, "parseAndAddBenefit err:", t);
        }
    }

    /**
     * 收录单个权益对象（briefConfigInfos / benefits / benefitInfo / benefitDetail 等数组或节点元素）。
     * 单条对象通常无 resultCode，不能走 parseAndAddBenefit 的校验入口。
     */
    public static void parseAndAddBenefitObject(JSONObject obj) {
        try {
            if (obj == null) {
                return;
            }
            collectBenefitFromObject(obj);
        } catch (Throwable t) {
            Log.err(TAG, "parseAndAddBenefitObject err:", t);
        }
    }

    private static void collectBenefitFromObject(JSONObject obj) {
        if (obj == null) {
            return;
        }
        // 对象本身就是单条权益（briefConfigInfos / benefits 数组元素）时直接收录
        if (obj.has("benefitId") && obj.has("name")) {
            addBenefitFromInfo(obj);
        }
        JSONArray entityInfoList = obj.optJSONArray("entityInfoList");
        if (entityInfoList != null) {
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entity = entityInfoList.optJSONObject(i);
                if (entity == null) {
                    continue;
                }
                JSONObject benefitInfo = entity.optJSONObject("benefitInfo");
                if (benefitInfo == null) {
                    benefitInfo = entity;
                }
                addBenefitFromInfo(benefitInfo);
            }
        }
        // 其它可能承载列表的字段（导航/闪蝶/搜索结果）
        JSONArray benefitList = obj.optJSONArray("benefitList");
        if (benefitList != null) {
            for (int i = 0; i < benefitList.length(); i++) {
                JSONObject info = benefitList.optJSONObject(i);
                if (info != null) {
                    addBenefitFromInfo(info);
                }
            }
        }
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object v = obj.opt(key);
            if (v instanceof JSONArray && !"topIds".equals(key) && !"deliveryIdList".equals(key) && !"entityIdList".equals(key)) {
                JSONArray arr = (JSONArray) v;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        JSONObject inner = (JSONObject) item;
                        if (inner.has("benefitInfo") || (inner.has("benefitId") && inner.has("name"))) {
                            collectBenefitFromObject(inner);
                        }
                    }
                }
            } else if (v instanceof JSONObject) {
                JSONObject inner = (JSONObject) v;
                if (inner.has("benefitInfo") || (inner.has("benefitId") && inner.has("name"))) {
                    collectBenefitFromObject(inner);
                }
            }
        }
    }

    private static void addBenefitFromInfo(JSONObject benefitInfo) {
        try {
            String benefitId = benefitInfo.optString("benefitId");
            if (benefitId.isEmpty()) {
                return;
            }
            String name = benefitInfo.optString("name");
            if (name.isEmpty()) {
                name = benefitInfo.optString("displayName");
            }
            String itemId = benefitInfo.optString("itemId");
            JSONObject price = benefitInfo.optJSONObject("pricePresentation");
            String strategyType = "";
            String point = "";
            String yuan = "";
            String grabHour = "-1";
            if (price != null) {
                strategyType = price.optString("strategyType");
                point = String.valueOf(price.optInt("point", 0));
                yuan = price.optString("yuan", "");
                if (price.has("grabHour")) {
                    grabHour = String.valueOf(price.optInt("grabHour", -1));
                }
            }
            // 价格兜底：pointPriceForDisplay / purePointForDisplay / 顶层 point/yuan
            if (point.isEmpty() || "0".equals(point)) {
                JSONObject pfd = benefitInfo.optJSONObject("pointPriceForDisplay");
                if (pfd != null) {
                    point = String.valueOf(pfd.optInt("minPoint", 0));
                    if (yuan.isEmpty()) {
                        yuan = pfd.optString("minAmount", "");
                    }
                }
            }
            if (point.isEmpty() || "0".equals(point)) {
                JSONObject ppd = benefitInfo.optJSONObject("purePointForDisplay");
                if (ppd != null) {
                    JSONObject primary = ppd.optJSONObject("primary");
                    if (primary != null) {
                        point = String.valueOf(primary.optInt("minPoint", 0));
                    }
                }
            }
            if (point.isEmpty() || "0".equals(point)) {
                point = String.valueOf(benefitInfo.optInt("point", 0));
            }
            if (yuan.isEmpty()) {
                yuan = benefitInfo.optString("yuan", benefitInfo.optString("cash", benefitInfo.optString("price", "")));
            }
            MemberBenefitIdMap.addBenefitDetail(benefitId, name, itemId, strategyType, point, yuan, grabHour, extractPicUrl(benefitInfo));
        } catch (Throwable t) {
            Log.err(TAG, "addBenefitFromInfo err:", t);
        }
    }

    /** 兼容解析商品图片 URL：多个候选字段，取第一个非空。 */
    private static String extractPicUrl(JSONObject obj) {
        if (obj == null) {
            return "";
        }
        // 真实 RPC 响应图片字段：picUrls.coverUrlList[0] / picUrls.iconUrl（已由抓包明文确认）
        JSONObject picUrls = obj.optJSONObject("picUrls");
        if (picUrls != null) {
            JSONArray coverList = picUrls.optJSONArray("coverUrlList");
            if (coverList != null && coverList.length() > 0) {
                String v = coverList.optString(0, "");
                if (!v.isEmpty()) {
                    return normalizePicUrl(v);
                }
            }
            String icon = picUrls.optString("iconUrl", "");
            if (!icon.isEmpty() && !"null".equalsIgnoreCase(icon)) {
                return normalizePicUrl(icon);
            }
        }
        String[] candidates = {"picUrl", "imageUrl", "iconUrl", "logoUrl", "imgUrl", "mainPic", "cover", "headPic", "bannerUrl", "pictureUrl", "img", "thumb"};
        for (String c : candidates) {
            String v = obj.optString(c, "");
            if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) {
                return normalizePicUrl(v);
            }
        }
        // 嵌套对象内再找一轮
        String[] subKeys = {"imageInfo", "picInfo", "icon", "logo", "cover", "imgInfo"};
        for (String sk : subKeys) {
            JSONObject sub = obj.optJSONObject(sk);
            if (sub != null) {
                String v = extractPicUrl(sub);
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return "";
    }

    /** 补齐相对协议：//xxx -> https://xxx */
    private static String normalizePicUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        return url;
    }

    /* ==================== 兑换主流程 ==================== */

    /**
     * 查询当前可用积分；失败返回 -1。
     */
    public static int queryPointBalance() {
        try {
            JSONObject jo = new JSONObject(AntMemberRpcCall.queryMemberInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryMemberInfo 失败 " + jo.optString("desc") + " raw=" + jo.toString());
                return -1;
            }
            // 兼容两种响应结构：优先 data 子对象，其次顶层（SJ 版为顶层 pointBalance）
            JSONObject data = jo.optJSONObject("data");
            if (data != null && data.has("pointBalance")) {
                return data.optInt("pointBalance", -1);
            }
            return jo.optInt("pointBalance", -1);
        } catch (Throwable t) {
            Log.err(TAG, "queryPointBalance err:", t);
            return -1;
        }
    }

    /**
     * 兑换用户在列表中勾选的权益（按 benefitId）。
     *
     * @param selectedIds     勾选集合
     * @param executeInterval 每次兑换间隔（毫秒）
     */
    public static void exchangeSelected(Set<String> selectedIds, int executeInterval) {
        try {
            if (selectedIds == null || selectedIds.isEmpty()) {
                Log.i(TAG, "兑换列表为空，跳过");
                return;
            }
            int pointBalance = queryPointBalance();
            if (pointBalance < 0) {
                return;
            }
            for (String benefitId : selectedIds) {
                if (benefitId == null || benefitId.isEmpty()) {
                    continue;
                }
                if (!Status.canMemberPointExchangeBenefitToday(benefitId)) {
                    continue;
                }
                String name = MemberBenefitIdMap.getRealName(benefitId);
                if (name == null || name.isEmpty()) {
                    name = benefitId;
                }
                int consumed = exchangeSingleTarget(name, pointBalance, executeInterval);
                if (consumed > 0) {
                    pointBalance -= consumed;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "exchangeSelected err:", t);
        }
    }

    /**
     * 兑换自定义商品（按名称，逗号/分号分隔）。
     */
    public static void exchangeCustom(String customStr, int executeInterval) {
        try {
            if (customStr == null || customStr.trim().isEmpty()) {
                return;
            }
            int pointBalance = queryPointBalance();
            if (pointBalance < 0) {
                return;
            }
            String[] names = customStr.split("[,，;；]");
            for (String n : names) {
                String name = n.trim();
                if (name.isEmpty()) {
                    continue;
                }
                int consumed = exchangeSingleTarget(name, pointBalance, executeInterval);
                if (consumed > 0) {
                    pointBalance -= consumed;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "exchangeCustom err:", t);
        }
    }

    /**
     * 单品兑换。
     * 返回消耗积分；0 表示未兑换/失败。
     */
    public static int exchangeSingleTarget(String name, int pointBalance, int executeInterval) {
        try {
            String benefitId = MemberBenefitIdMap.getBenefitId(name);
            if (benefitId == null) {
                benefitId = MemberBenefitIdMap.searchBenefitByKeyword(name);
            }
            if (benefitId == null) {
                Log.i(TAG, "未找到权益[" + name + "]，尝试搜索补全");
                parseAndAddBenefit(AntMemberRpcCall.searchMemberBenefit(name, 1, 20));
                benefitId = MemberBenefitIdMap.getBenefitId(name);
                if (benefitId == null) {
                    benefitId = MemberBenefitIdMap.searchBenefitByKeyword(name);
                }
            }
            if (benefitId == null) {
                Log.i(TAG, "[" + name + "] 搜索后仍未收录，跳过");
                return 0;
            }

            // 详情缺失时补详情
            if (MemberBenefitIdMap.getItemId(benefitId).isEmpty() || MemberBenefitIdMap.getStrategyType(benefitId).isEmpty()) {
                parseAndAddBenefit(AntMemberRpcCall.querySingleBenefitDetail(benefitId));
            }

            String itemId = MemberBenefitIdMap.getItemId(benefitId);
            String strategyType = MemberBenefitIdMap.getStrategyType(benefitId);
            if (itemId.isEmpty() || strategyType.isEmpty()) {
                Log.i(TAG, "[" + name + "] 详情不足（itemId=" + itemId + ", strategyType=" + strategyType + "），跳过");
                return 0;
            }

            int point = parsePositive(MemberBenefitIdMap.getPoint(benefitId));
            String yuan = MemberBenefitIdMap.getYuan(benefitId);
            if ("POINT_CASH_PAY".equalsIgnoreCase(strategyType) || isPositiveAmount(yuan)) {
                // 混合支付：需现金（yuan>0）或 POINT_CASH_PAY 策略，走官方 LinkMall 收银台调起
                return startMixedPayment(name, benefitId, itemId, point, pointBalance);
            }
            if (point > 0 && pointBalance > 0 && point > pointBalance) {
                Log.i(TAG, "[" + name + "] 积分不足 need=" + point + " have=" + pointBalance);
                return 0;
            }

            JSONObject jo = new JSONObject(AntMemberRpcCall.exchangeMemberBenefit(benefitId, itemId, strategyType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Status.memberPointExchangeBenefitToday(benefitId);
                Log.other("会员积分兑换[" + name + "]#花费[" + point + "积分]");
                return point;
            }

            // 失败回退：切换 POINT_CASH_PAY 再试一次（PARAM_ILLEGAL 切换策略）
            String resultCode = jo.optString("resultCode");
            if ("PARAM_ILLEGAL".equals(resultCode) || "SYSTEM_ERROR".equals(resultCode)) {
                Log.i(TAG, "[" + name + "] " + strategyType + " 失败(" + resultCode + ")，尝试 POINT_CASH_PAY");
                JSONObject jo2 = new JSONObject(AntMemberRpcCall.exchangeMemberBenefit(benefitId, itemId, "POINT_CASH_PAY"));
                if (MessageUtil.checkResultCode(TAG, jo2)) {
                    Status.memberPointExchangeBenefitToday(benefitId);
                    Log.other("会员积分兑换[" + name + "]#切换混合支付成功，需人工完成支付");
                    return point;
                }
            }
            Log.i(TAG, "[" + name + "] 兑换失败 " + resultCode + " " + jo.optString("desc"));
        } catch (Throwable t) {
            Log.err(TAG, "exchangeSingleTarget err:", t);
        }
        return 0;
    }

    /* ==================== 混合支付（LinkMall 收银台调起） ==================== */

    /** 判断现金金额是否非 0（null/空/0/0.0/0.00 视为纯积分）。 */
    private static boolean isPositiveAmount(String yuan) {
        return yuan != null && !yuan.isEmpty()
                && !"0".equals(yuan) && !"0.0".equals(yuan) && !"0.00".equals(yuan);
    }

    /**
     * 混合支付实物商品：通过官方 LinkMall 通道发起下单并拉起支付宝收银台。
     * POINT_CASH_PAY 分支：
     * 取 skuInfoList 中可售 skuId（无则回退 benefitDetail.skuList[0]），
     * 构造 alipays:// 链接后以 NEW_TASK 拉起；返回消耗积分，失败返回 0。
     */
    private static int startMixedPayment(String name, String benefitId, String itemId, int point, int pointBalance) {
        try {
            String yuan = MemberBenefitIdMap.getYuan(benefitId);
            Log.record("会员积分[" + name + "]为混合支付实物商品(需" + point + "积分 + " + yuan + "元)，通过官方LinkMall通道发起下单");
            if (point > 0 && pointBalance > 0 && point > pointBalance) {
                Log.i(TAG, "[" + name + "] 积分不足 need=" + point + " have=" + pointBalance);
                return 0;
            }

            JSONObject jo = new JSONObject(AntMemberRpcCall.querySingleBenefitDetail(benefitId));
            String skuId = pickSkuId(jo);
            if (skuId.isEmpty()) {
                Log.i(TAG, "[" + name + "] 未取到 skuId，详情响应=" + (jo.toString().length() > 800 ? jo.toString().substring(0, 800) + "..." : jo.toString()));
                return 0;
            }
            if (!launchScheme(createAliyunLink(itemId, skuId, point))) {
                Log.i(TAG, "[" + name + "] 混合支付收银台调起失败");
                return 0;
            }
            Status.memberPointExchangeBenefitToday(benefitId);
            Log.other("会员积分[" + name + "]混合支付下单成功调起! 消耗" + point + "积分" + (yuan != null && !yuan.isEmpty() ? " + " + yuan + "元" : "") + "，请手动完成支付");
            return point;
        } catch (Throwable t) {
            Log.err(TAG, "startMixedPayment err:", t);
        }
        return 0;
    }

    /** 从详情响应中选取可售 skuId；无 skuInfoList 时回退 benefitDetail.skuList[0]。 */
    private static String pickSkuId(JSONObject jo) {
        try {
            JSONObject itemInfo = jo.optJSONObject("itemInfo");
            if (itemInfo != null) {
                JSONArray skus = itemInfo.optJSONArray("skuInfoList");
                if (skus != null && skus.length() > 0) {
                    for (int i = 0; i < skus.length(); i++) {
                        JSONObject sku = skus.optJSONObject(i);
                        if (sku == null) {
                            continue;
                        }
                        if (sku.optBoolean("canSell", true)) {
                            String sid = sku.optString("skuId", "");
                            if (!sid.isEmpty() && !"0".equals(sid)) {
                                return sid;
                            }
                        }
                    }
                }
            }
            JSONObject bd = jo.optJSONObject("benefitDetail");
            if (bd != null) {
                JSONArray skuList = bd.optJSONArray("skuList");
                if (skuList != null && skuList.length() > 0) {
                    JSONObject first = skuList.optJSONObject(0);
                    if (first != null) {
                        return first.optString("skuId", "");
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "pickSkuId err:", t);
        }
        return "";
    }

    /** 构造官方 LinkMall 混合支付链接。 */
    private static String createAliyunLink(String itemId, String skuId, int point) {
        try {
            String orderItems = URLEncoder.encode("[{\"itemId\":\"" + itemId.trim() + "\",\"skuId\":\"" + skuId.trim() + "\",\"number\":1}]", "UTF-8");
            String lmPages = "https://pages.tmall.com/wow/wt/act/lm-pages?" + ("wh_page=buy&orderItems=" + orderItems + "&verifyPoint=" + point + "&env=");
            String goToUrl = "https://pages.tmall.com/wow/z/wt/act/alipay-login?goToUrl=" + URLEncoder.encode(lmPages, "UTF-8");
            return "alipays://platformapi/startapp?appId=20000067&backBehavior=back&showOptionMenu=NO&url=" + URLEncoder.encode(goToUrl, "UTF-8");
        } catch (Throwable t) {
            Log.err(TAG, "createAliyunLink err:", t);
            return "";
        }
    }

    /** 以 NEW_TASK 拉起外部链接。 */
    private static boolean launchScheme(String url) {
        try {
            Context context = ApplicationHook.getContext();
            if (context == null || url == null || url.isEmpty()) {
                return false;
            }
            Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "launchScheme err:", t);
            return false;
        }
    }

    /* ==================== 整点秒杀 ==================== */

    /**
     * 调度最近一个整点秒杀任务；到点自动执行，执行完再排下一次。
     *
     * @param model      所属 Model（用于 addChildTask）
     * @param times      秒杀时间点，逗号分隔 "HH:mm"
     * @param enabled    开关
     */
    public static void scheduleSecKill(ModelTask model, String times, boolean enabled) {
        try {
            if (!enabled || model == null || times == null) {
                return;
            }
            long now = System.currentTimeMillis();
            long best = -1;
            String bestText = "";
            String[] parts = times.split("[,，;；]");
            for (String p : parts) {
                String t = p.trim();
                if (t.isEmpty()) {
                    continue;
                }
                long millis = nextMillisOfHourMinute(t);
                if (millis > 0 && (best == -1 || millis < best)) {
                    best = millis;
                    bestText = t;
                }
            }
            if (best <= 0) {
                Log.i(TAG, "秒杀时间配置无效或已过今日全部时间点，未调度");
                return;
            }
            long delay = best - now;
            final String taskId = "AntMemberSecKill_" + best;
            model.addChildTask(new ModelTask.ChildModelTask(taskId, "antMember",
                    AntMemberExchange::executeSecKill, now + Math.max(0, delay - 500)));
            Log.i(TAG, "已调度整点秒杀 " + bestText + "（" + (delay / 1000) + "s 后）");
        } catch (Throwable t) {
            Log.err(TAG, "scheduleSecKill err:", t);
        }
    }

    /** 秒杀执行体：批量兑换当前整点 grabHour 匹配的权益，并排下一次。 */
    public static void executeSecKill() {
        try {
            doBurstExchange();
        } catch (Throwable t) {
            Log.err(TAG, "executeSecKill err:", t);
        }
    }

    /**
     * 整点批量兑换：拉全库后，兑换 grabHour 等于当前小时的权益各一次。
     */
    public static void doBurstExchange() {
        try {
            fetchDynamicBenefits();
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            int pointBalance = queryPointBalance();
            if (pointBalance < 0) {
                return;
            }
            int done = 0;
            for (String benefitId : MemberBenefitIdMap.getMap().keySet()) {
                String grabHour = MemberBenefitIdMap.getGrabHour(benefitId);
                if (grabHour.isEmpty() || "-1".equals(grabHour)) {
                    continue;
                }
                if (parsePositive(grabHour) != hour) {
                    continue;
                }
                if (!Status.canMemberPointExchangeBenefitToday(benefitId)) {
                    continue;
                }
                String name = MemberBenefitIdMap.getRealName(benefitId);
                if (name == null || name.isEmpty()) {
                    name = benefitId;
                }
                int consumed = exchangeSingleTarget(name, pointBalance, 300);
                if (consumed > 0) {
                    pointBalance -= consumed;
                    done++;
                }
            }
            Log.other("会员积分秒杀[" + hour + "点]完成，成功" + done + "个");
        } catch (Throwable t) {
            Log.err(TAG, "doBurstExchange err:", t);
        }
    }

    /* ==================== 工具方法 ==================== */

    /** 计算今天下一个 HH:mm 的毫秒时间戳；已过则返回 0。 */
    private static long nextMillisOfHourMinute(String hhmm) {
        try {
            String[] hp = hhmm.split(":");
            if (hp.length < 2) {
                return 0;
            }
            int h = Integer.parseInt(hp[0].trim());
            int m = Integer.parseInt(hp[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return 0;
            }
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, h);
            c.set(Calendar.MINUTE, m);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long t = c.getTimeInMillis();
            if (t <= System.currentTimeMillis()) {
                return 0;
            }
            return t;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int parsePositive(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (Throwable t) {
            return 0;
        }
    }
}
