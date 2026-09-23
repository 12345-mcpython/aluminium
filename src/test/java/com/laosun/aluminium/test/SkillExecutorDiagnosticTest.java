package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * 非伤害技能的**未分派诊断**（P8-2 计划第 3 条）。
 *
 * <p>背景：{@code SkillExecutor.resolveHits} 在"不是伤害类技能"时**静默 return** ——
 * 治疗/护盾/buff/控制/召唤被施放后**什么都不发生**（只有回能照给）。
 * 真实队伍里很难察觉：日志上技能"放出去了"，只是没有任何效果。
 *
 * <p>所以加了一个可开关的诊断日志（归属标注到 P6-2 / P10-3 / P10-6 / P9-4）。
 * 计划原本想直接 {@code IO.println}，但 demo 每回合都在放治疗与护盾 —— 默认会刷屏，
 * 故改为显式开关。这个类同时验证"开了会打印"与"默认不打印"。
 */
public class SkillExecutorDiagnosticTest {
    private static final double EPS = 1e-9;

    @AfterEach
    public void restore() {
        SkillExecutor.setLogNotDispatched(false);      // 别把开关漏给别的测试
    }

    /**
     * 打开开关后，放一个治疗技（娜塔莎战技 = {@code Restore}）会打印未分派提示，
     * 且标注归属 P6-2。
     */
    @Test
    public void diagnosticReportsNonDamagingSkillsWhenEnabled() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha));
        });

        Assertions.assertTrue(out.contains("未分派"), "应当打印未分派提示，实际输出：" + out);
        Assertions.assertTrue(out.contains("RESTORE"), "应当带效果类型，实际：" + out);
        Assertions.assertTrue(out.contains("娜塔莎") || out.contains("Natasha"),
                "应当带施放者，实际：" + out);
        Assertions.assertTrue(out.contains("P6-2"), "应当标注归属阶段，实际：" + out);
    }

    /**
     * **默认关闭**：不打印。demo 每回合都在治疗/护盾，默认开会刷屏。
     */
    @Test
    public void diagnosticIsOffByDefault() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);

        String out = capture(() ->
                battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha)));

        Assertions.assertFalse(out.contains("未分派"),
                "默认不该打印诊断，实际输出：" + out);
    }

    /**
     * 护盾技（三月七战技 = {@code Defence}）走的是同一条静默路径，归属 P10-3。
     */
    @Test
    public void shieldSkillIsAlsoReported() {
        Character march7th = CharacterFactory.create(1001, 80);
        Battle battle = newBattle(march7th);

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1001, 2, 1), march7th, List.of(march7th));
        });

        Assertions.assertTrue(out.contains("DEFENCE"), "应当报告 DEFENCE，实际：" + out);
        Assertions.assertTrue(out.contains("P10-3"), "应当标注 P10-3，实际：" + out);
    }

    /**
     * 伤害技能**不**触发诊断（它走的是正常分派路径）。
     */
    @Test
    public void damagingSkillsDoNotTriggerTheDiagnostic() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), new Random(0));
        battle.startBattle();

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1204, 1, 1), jingYuan, List.of(enemy));
        });

        Assertions.assertFalse(out.contains("未分派"), "伤害技能不该报告，实际：" + out);
        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp(), "它确实打出去了");
    }

    /**
     * 诊断**不影响行为**：非伤害技能仍然是"只有回能、没有效果"。
     *
     * <p>这条把当前的真实状态钉住 —— 打开日志不等于实现了效果。
     */
    @Test
    public void diagnosticDoesNotChangeBehaviour() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);
        natasha.takeDamage(natasha.getMaxHp() / 2);      // currentHp 没有 setter，用受伤制造缺口
        double hpBefore = natasha.getCurrentHp();
        double energyBefore = natasha.getCurrentEnergy();
        Assertions.assertTrue(hpBefore < natasha.getMaxHp(), "确实掉了血，才有得治");

        SkillExecutor.setLogNotDispatched(true);
        battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha));

        Assertions.assertEquals(hpBefore, natasha.getCurrentHp(), EPS,
                "治疗技仍然不生效（效果是 P6-2 的另一条路径，不走本执行器）");
        Assertions.assertTrue(natasha.getCurrentEnergy() > energyBefore,
                "但回能照给（P3-2：技能回能与有没有伤害无关）");
    }

    // ==================================================================

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** 捕获 {@code System.out}（{@code IO.println} 就是写 stdout）。 */
    private static String capture(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
