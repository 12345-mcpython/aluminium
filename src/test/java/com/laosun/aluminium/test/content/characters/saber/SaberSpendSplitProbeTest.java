package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Probe: a staircase of conditions on SKILL_CAST - which step stops firing? */
public class SaberSpendSplitProbeTest {

    @Test
    public void probe() {
        Character saber = CharacterFactory.create(1014, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        for (int i = 0; i < 45; i++) {
            saber.getBuffManager().addBuff(new StackBuff("炉心共鸣", 1, true, 60));
        }
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[probe] mana=" + saber.getBuffManager().hasState("魔力放出")
                + " s1=" + saber.getBuffManager().hasState("probe_s1")
                + " s2=" + saber.getBuffManager().hasState("probe_s2")
                + " s3=" + saber.getBuffManager().hasState("probe_s3")
                + " s4=" + saber.getBuffManager().hasState("probe_s4")
                + " stacks=" + saber.getBuffManager().stacksOf("炉心共鸣")
                + " energy=" + saber.getCurrentEnergy());
    }
}