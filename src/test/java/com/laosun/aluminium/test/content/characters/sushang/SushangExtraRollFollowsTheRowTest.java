package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1206 Sushang (素裳), her ultimate's rider: 「通过额外判定发动的【剑势】伤害为<b>原伤害的 50%</b>」.
 *
 * <p>⚠ <b>「原伤害」 is the SKILL ROW's 剑势 share, not 100% ATK.</b> The base roll reads {@code SKILL:1} (the skill's 2nd
 * column: 1.0 at level 10, <b>1.1</b> at level 12), and Eidolon 5 raises the skill by two levels - so a literal
 * {@code percent: 0.5} silently under-pays by 9% from E5 on. The fix is the spelling this case exists to pin:
 * {@code percent: 0.5} <b>beside</b> {@code percent_from_skill_param: "SKILL:1"}, which the engine reads as "50% of that
 * share".
 *
 * <p>⚠ <b>Why the reading is a ratio between the two Eidolon ranks, per instance.</b> The absolute number depends on ATK,
 * on the ultimate's own +30%/32.4% ATTACK buff (Eidolon 3 raises THAT row) and on the trace that adds 2.5% per accumulated
 * 剑势 - all of which differ between the runs or depend on order. Dividing each run's instance by that run's OWN attack
 * power cancels all of it: what is left is {@code share × zones}, and its rank ratio is exactly the row's own ratio
 * (1.1). Pinning only E0 cannot tell a literal from a row read (measured: both read 0.5·1.0 there).
 */
public class SushangExtraRollFollowsTheRowTest {
    private static final int SUSHANG = 1206;
    private static final int ALLY = 1204;

    /**
     * Every 【剑势】instance of one skill cast, as a share of the attacker's own ATK, in settlement order.
     */
    @Test
    public void everySwordStanceInstanceFollowsTheSkillRow() {
        List<Double> atZero = swordStancePerAttack(0);
        List<Double> atFive = swordStancePerAttack(5);
        System.out.println("[1206] 剑势每一发 ÷ 攻击力: E0 " + atZero + " ; E5 " + atFive);
        Assertions.assertEquals(3, atZero.size(),
                "precondition: 1 次基础判定 + 2 次额外判定（大招标记在场）");
        Assertions.assertEquals(atZero.size(), atFive.size(), "两档的段数必须一致，否则比不了");
        for (int i = 0; i < atZero.size(); i++) {
            Assertions.assertEquals(1.1, atFive.get(i) / atZero.get(i), 1e-9,
                    "第 " + i + " 发剑势也必须随战技那一行走：E5 之后该行是 1.1 倍"
                            + "（E0=" + atZero + " / E5=" + atFive + "）");
        }
    }

    // ==================================================================

    /**
     * One battle: the ultimate opens the 2-extra-roll marker, then one skill cast settles 剑势.
     */
    private static List<Double> swordStancePerAttack(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(SUSHANG, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Character sushang = battle.characters().getFirst();

        // "施放战技时额外增加 2 次【剑势】的发动判定": the ultimate installs it for 2 turns.
        sushang.setCurrentEnergy(sushang.getMaxEnergy());
        battle.castUltra(sushang, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double atk = sushang.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, sushang.getSkills().get(SkillType.SKILL), sushang,
                List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();

        return settled.stream()
                .filter(d -> d.getType() == DamageType.ADDITIONAL)
                .map(d -> d.breakdown().get("final") / atk)
                .toList();
    }
}
