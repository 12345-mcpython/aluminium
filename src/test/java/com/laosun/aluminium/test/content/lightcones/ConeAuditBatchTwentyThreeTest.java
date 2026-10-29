package com.laosun.aluminium.test.content.lightcones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The twenty-third audit batch of light cones (23031-23036).
 *
 * <p>No content defects in this batch: all six already read correctly clause by clause. The readings below pin what the files
 * say, including the layered ones (23031's 【流光】 decay, 23034's 【圣咏】 on the TARGET, 23035's break-damage scope).
 */
public class ConeAuditBatchTwentyThreeTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 23031 我将，巡征追猎: 【流光】 stacks on a follow-up (two at most), each layer gives ult defence ignore, one decays a turn.
     */
    @Test
    public void coneTwoThreeThreeOneGlowStacksAndDecays() {
        JsonObject stack = effect(23031, "cone23031_glow_on_follow_up", 0);
        JsonObject ignore = effect(23031, "cone23031_ult_defence_ignore", 0);
        System.out.println("[23031] 流光: max=" + stack.get("max_stacks") + " ; 无视防御 per_stack=" + ignore.get("per_stack")
                + " from_category=" + rule(23031, "cone23031_ult_defence_ignore").getAsJsonArray("when"));
        Assertions.assertEquals(2, stack.get("max_stacks").getAsInt(), "「最多叠加 2 层」");
        Assertions.assertEquals("self_stacks:流光", ignore.get("per_stack").getAsString(), "「**每层**使终结技无视防御」");
        Assertions.assertTrue(rule(23031, "cone23031_ult_defence_ignore").getAsJsonArray("when").toString()
                .contains("ULTRA"), "「终结技伤害」");
        Assertions.assertEquals(1, effect(23031, "cone23031_glow_decay", 0).get("amount").getAsInt(), "「回合结束移除 1 层」");
    }

    /**
     * 23032 唯有香如故: the break share once, and 【忘忧】 raises the damage the target takes, more at 150% break.
     */
    @Test
    public void coneTwoThreeThreeTwoForgetWorry() {
        Fixture f = new Fixture(23032);
        Fixture bare = new Fixture(null);
        double breaking = f.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get()
                - bare.wearer.getAttribute(AttributeType.BREAKING_EFFECT).get();
        System.out.println("[23032] 击破特攻增量 " + breaking);
        Assertions.assertEquals(0.6, breaking, 1e-9, "「击破特攻提高 60%」—— 光锥属性给一次");

        JsonObject mark = effect(23032, "cone23032_forget_worry", 0);
        System.out.println("[23032] 忘忧: turns=" + mark.get("turns") + " target=" + mark.get("target"));
        Assertions.assertEquals(2, mark.get("turns").getAsInt(), "「持续 2 回合」");
        com.google.gson.JsonArray extra = rule(23032, "cone23032_extra_at_high_break").getAsJsonArray("when");
        Assertions.assertTrue(extra.toString().contains("BREAKING_EFFECT >= 1.5"), "「击破特攻 ≥ 150% 时额外提高」");
    }

    /**
     * 23033 忍法帖: energy at battle start, and two basics after an ultimate advance her action.
     */
    @Test
    public void coneTwoThreeThreeThreeMineAndAdvance() {
        JsonObject energy = effect(23033, "cone23033_energy_at_start", 0);
        System.out.println("[23033] 开战回能 " + energy.get("amount"));
        Assertions.assertEquals(30.0, energy.get("amount").getAsDouble(), 1e-9, "「进入战斗立即恢复 30 点能量」（叠影 1）");
        JsonObject pay = effect(23033, "cone23033_mine_pays_out", 0);
        System.out.println("[23033] 行动提前 " + pay.get("percent"));
        Assertions.assertEquals(0.5, pay.get("percent").getAsDouble(), 1e-9, "「行动提前 50%」");
        Assertions.assertEquals(1, effect(23033, "cone23033_mine_pays_out", 2).get("amount").getAsInt(), "「并移除【雷遁】」");
    }

    /**
     * 23034 回到大地的飞行: the 【圣咏】 stack belongs to the TARGET and its share is per layer; two casts pay a skill point.
     */
    @Test
    public void coneTwoThreeThreeFourChantBelongsToTheTarget() {
        JsonObject stack = effect(23034, "cone23034_chant_for_the_target_skill", 1);
        JsonObject share = effect(23034, "cone23034_chant_for_the_target_skill", 2);
        System.out.println("[23034] 圣咏: target=" + stack.get("target") + " max=" + stack.get("max_stacks")
                + " ; 份额 per_stack=" + share.get("per_stack") + " target=" + share.get("target"));
        Assertions.assertEquals("target", stack.get("target").getAsString(), "「技能**目标**获得 1 层【圣咏】」");
        Assertions.assertEquals(3, stack.get("max_stacks").getAsInt(), "「最多叠加 3 层」");
        Assertions.assertEquals("圣咏", share.get("per_stack").getAsString(), "「每层使**持有者**造成的伤害提高」");

        Fixture f = new Fixture(23034);
        f.wearer.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, f.wearer, f.ally, 0, 0));
        f.battle.processRequests();
        System.out.println("[23034] 对队友施放战技后的能量 " + f.wearer.getCurrentEnergy() + " ; 队友的增伤 "
                + f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        Assertions.assertTrue(f.wearer.getCurrentEnergy() >= 6, "「恢复 6 点能量」");
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > 0, "队友吃到了【圣咏】");
    }

    /**
     * 23035 长路终有归途: the burn state is scoped to BREAK damage and stacks twice.
     */
    @Test
    public void coneTwoThreeThreeFiveBurnScopesBreakDamage() {
        JsonObject stack = effect(23035, "cone23035_burn_the_broken", 0);
        JsonObject taken = effect(23035, "cone23035_burn_the_broken", 1);
        System.out.println("[23035] 焚灼: chance=" + stack.get("base_chance") + " max=" + stack.get("max_stacks")
                + " ; 增伤 damage_type=" + taken.get("damage_type") + " per_stack=" + taken.get("per_stack"));
        Assertions.assertEquals(1.0, stack.get("base_chance").getAsDouble(), 1e-12, "「100% 基础概率」");
        Assertions.assertEquals(2, stack.get("max_stacks").getAsInt(), "「可叠加 2 层」");
        Assertions.assertEquals("BREAK", taken.get("damage_type").getAsString(), "「受到的**击破伤害**提高」");
        Assertions.assertEquals("焚灼", taken.get("per_stack").getAsString(), "「每层」");
    }

    /**
     * 23036 将光阴织成黄金: 【织锦】 stacks from her or her memosprite, six at most, and the cap adds basic-attack damage.
     */
    @Test
    public void coneTwoThreeThreeSixBrocadeStacks() {
        JsonObject stack = effect(23036, "cone23036_wearer_weaves", 0);
        JsonObject summon = rule(23036, "cone23036_summon_weaves");
        JsonObject crit = effect(23036, "cone23036_brocade_crit_damage", 0);
        JsonObject cap = rule(23036, "cone23036_cap_basic_attack");
        System.out.println("[23036] 织锦: max=" + stack.get("max_stacks") + " ; 忆灵那条 when=" + summon.getAsJsonArray("when")
                + " ; 满层普攻 when=" + cap.getAsJsonArray("when"));
        Assertions.assertEquals(6, stack.get("max_stacks").getAsInt(), "「最多叠加 6 层」");
        Assertions.assertTrue(summon.getAsJsonArray("when").toString().contains("summon"), "「装备者和忆灵在攻击后」");
        Assertions.assertEquals("self_stacks:织锦", crit.get("per_stack").getAsString(), "「每层暴伤提高」");
        Assertions.assertTrue(cap.getAsJsonArray("when").toString().contains(">= 6"), "「叠加至上限时」");
    }

    // ==================================================================

    private static JsonObject rule(int cone, String id) {
        try (java.io.InputStream stream = ConeAuditBatchTwentyThreeTest.class
                .getResourceAsStream("/game/light_cones/" + cone + ".json")) {
            Assertions.assertNotNull(stream, "missing cone " + cone);
            JsonObject root = JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (com.google.gson.JsonElement each : root.getAsJsonArray("1")) {
                JsonObject candidate = each.getAsJsonObject();
                if (id.equals(candidate.get("id").getAsString())) {
                    return candidate;
                }
            }
            throw new AssertionError("no rule " + id);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonObject effect(int cone, String id, int index) {
        return rule(cone, id).getAsJsonArray("do").get(index).getAsJsonObject();
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            this.battle = new Battle(List.of(wearer, ally), List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }
    }
}
