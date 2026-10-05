package com.xse.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xse.sesame.util.idMap.MemberBenefitIdMap;

public class MemberBenefit extends IdAndName {

    /** 商品图片 URL（可能为空） */
    public String pic;
    /** 所需积分（可能为空） */
    public String point;
    /** 现金价（可能为空） */
    public String yuan;

    public MemberBenefit(String i, String n) {
        id = i;
        name = n;
    }

    public MemberBenefit(String i, String n, String pic, String point, String yuan) {
        id = i;
        name = n;
        this.pic = pic;
        this.point = point;
        this.yuan = yuan;
    }

    public static List<MemberBenefit> getList() {
        List<MemberBenefit> list = new ArrayList<>();
        Set<Map.Entry<String, String>> idSet = MemberBenefitIdMap.getMap().entrySet();
        for (Map.Entry<String, String> entry: idSet) {
            String id = entry.getKey();
            list.add(new MemberBenefit(id, entry.getValue(),
                    MemberBenefitIdMap.getPic(id),
                    MemberBenefitIdMap.getPoint(id),
                    MemberBenefitIdMap.getYuan(id)));
        }
        return list;
    }
}
