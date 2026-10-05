package com.xse.sesame.model.base;

import java.util.ArrayList;
import java.util.List;

import com.xse.sesame.data.Model;
import com.xse.sesame.model.extensions.ExtensionsHandle;
import com.xse.sesame.model.normal.answerAI.AnswerAI;
import com.xse.sesame.model.normal.base.BaseModel;
import com.xse.sesame.model.task.antAnimalGuess.AntAnimalGuess;
import com.xse.sesame.model.task.plantingFlowers.PlantingFlowers;
import com.xse.sesame.model.task.antDodo.AntDodo;
import com.xse.sesame.model.task.antFarm.AntFarm;
import com.xse.sesame.model.task.antForest.AntForestV2;
import com.xse.sesame.model.task.antMember.AntMember;
import com.xse.sesame.model.task.antOcean.AntOcean;
import com.xse.sesame.model.task.antOrchard.AntOrchard;
import com.xse.sesame.model.task.consumeGold.ConsumeGold;
import com.xse.sesame.model.task.antSports.AntSports;
import com.xse.sesame.model.task.antStall.AntStall;
import com.xse.sesame.model.task.greenFinance.GreenFinance;
import com.xse.sesame.model.task.goldenbeans.goldenbeans;
import com.xse.sesame.model.task.protectEcology.ProtectEcology;
import lombok.Getter;

public class ModelOrder {

    @Getter
    private static final List<Class<? extends Model>> clazzList = new ArrayList<>();

    static {
        clazzList.add(BaseModel.class);
        clazzList.add(AntForestV2.class);
        clazzList.add(AntFarm.class);
        clazzList.add(AntStall.class);
        clazzList.add(AntOrchard.class);
        clazzList.add(ProtectEcology.class);
        clazzList.add(AntDodo.class);
        clazzList.add(AntAnimalGuess.class);
        clazzList.add(PlantingFlowers.class);
        clazzList.add(AntOcean.class);
        clazzList.add(AntSports.class);
        clazzList.add(AntMember.class);
        clazzList.add(GreenFinance.class);
        clazzList.add(ConsumeGold.class);
        clazzList.add(goldenbeans.class);
        clazzList.add(AnswerAI.class);

        ExtensionsHandle.handleAlphaRequest("ModelOrder", "addExtensionsClass", clazzList);
    }
}