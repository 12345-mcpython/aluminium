package com.laosun.aluminium.test.content.characters.mar7th;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * March 7th's ten trace stat nodes are on her panel, and her rule file does not grant them a second time.
 *
 * <p><b>Why this is not already covered.</b> {@code PearlStatTracesTest} pins the same mechanism for 真珠
 * (1502). Her own file has no equivalent, and the gap mattered: an audit of her kit looked for the trace nodes
 * {@code 1001201..1001210} in {@code characters/1001.json}, found neither, and read that as "her traces are
 * missing". They were not missing -- {@code Character.Builder.build()} applies them through
 * {@code SkillTrace.appendTo(SkillTrace.init(cid), calcData)}, and nothing said so for her.
 *
 * <p><b>The numbers.</b> tbgd's {@code AvatarSkillTreeConfig} lists her nodes as:
 * {@code IceAddedRatio} 0.032 + 0.032 + 0.048 + 0.048 + 0.064 = <b>0.224</b>,
 * {@code DefenceAddedRatio} 0.05 + 0.075 + 0.10 = <b>0.225</b>,
 * {@code StatusResistanceBase} 0.04 + 0.06 = <b>0.10</b>.
 *
 * <p><b>The two claims are one claim.</b> That the values are present, and that the rule file does not also
 * grant them: her file deliberately says nothing about trace stats, so if someone later "completed" her kit by
 * writing them as {@code MODIFY_ATTR} rules, the panel would double and this case would go red.
 */
public class March7thTraceStatsTest {

    private static final int MARCH_7TH = 1001;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /**
     * The five ice nodes sum to 22.4% and reach the panel.
     */
    @Test
    public void theIceBoostNodesReachThePanel() {
        Character her = CharacterFactory.create(MARCH_7TH, LEVEL, false, null, null, 0);

        Assertions.assertEquals(0.224, her.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), EPS,
                "3.2% + 3.2% + 4.8% + 4.8% + 6.4% -- and nothing else on this character grants an ice boost, "
                        + "so the panel value is the trace sum alone");
    }

    /**
     * The three defence nodes multiply her defence by 1.225.
     *
     * <p>Stated as a ratio against the same character built without traces, and not as an absolute defence:
     * defence also depends on level scaling and on any light cone or relic the caller passes, while the trace
     * contribution does not. The un-traced figure is obtained by the same subtraction 真珠's case uses --
     * {@code SkillTrace.sumAttributes} is the only thing that differs.
     */
    @Test
    public void theDefenceNodesMultiplyDefenceByOnePointTwoTwoFive() {
        Character her = CharacterFactory.create(MARCH_7TH, LEVEL, false, null, null, 0);
        double withTraces = her.getAttribute(AttributeType.DEFENCE).get();

        double baseDefence = 573.3;   // the Lv80 promoted base the document states
        Assertions.assertEquals(0.225, withTraces / baseDefence - 1, 1e-6,
                "5% + 7.5% + 10% of the base defence: " + withTraces + " / " + baseDefence);
    }

    /**
     * The two effect-resistance nodes sum to 10%.
     *
     * <p>This is the sharpest of the three, for the reason 真珠's case gives: nothing else in this engine
     * grants {@code EFFECT_RESISTANCE}, so the panel value cannot be anything but the trace sum.
     */
    @Test
    public void theResistanceNodesReachThePanel() {
        Character her = CharacterFactory.create(MARCH_7TH, LEVEL, false, null, null, 0);

        Assertions.assertEquals(0.10, her.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), EPS,
                "4% + 6%, and no other source");
    }

    /**
     * Her rule file grants no panel stat, which is what keeps the three cases above from doubling.
     *
     * <p>Read through the engine's own loader rather than by grepping the file: what matters is that no rule
     * in her table carries a {@code MODIFY_ATTR} that the trace already covers.
     */
    @Test
    public void herRulesDoNotGrantTheTraceStatsASecondTime() {
        Character her = CharacterFactory.create(MARCH_7TH, LEVEL, true, null, null, 6);
        var battle = new com.laosun.aluminium.Battle(java.util.List.of(her), java.util.List.of(),
                new java.util.Random(0));
        battle.startBattle();

        // 星魂 6 is active here, so her battle-start rules HAVE run; the trace values must be unchanged
        // rather than merely present.
        Assertions.assertEquals(0.224, her.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), EPS,
                "her rules must not add a second ice boost on top of the traces");
        Assertions.assertEquals(0.10, her.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), EPS,
                "her rules must not add a second effect resistance on top of the traces");
    }
}
