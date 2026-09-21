package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * P7-4 的懒加载护栏：{@code stage.json}（9 MB / 约 2.9 万条关卡）不该在
 * {@code Constant} 初始化时被解析，而应等到第一次真的要关卡时。
 *
 * <p>为什么值得有条护栏：{@code stage.json} 比其它所有数据加起来还大。
 * 把它挪回静态块，等于每次 {@code Constant} 初始化都多付 ~35 MB 堆 + 几十毫秒。
 *
 * <p><b>怎么做到与用例顺序无关</b>：不看运行期状态，直接看 {@code Constant.class} 的
 * 静态初始化块里有没有关卡相关符号。
 * 运行期状态（解析了几次）在共享 JVM 里**必然**依赖哪个测试类先跑 ——
 * 我第一版就是这么写的：单独跑绿、全套红。而 class 文件是死的，谁都改不了它。
 */
public class StageLazyLoadTest {

    /**
     * 关卡表只解析一次（缓存语义），且返回的就是同一份实例。
     */
    @Test
    public void stagesAreParsedOnceAndCached() {
        Map<Integer, StageBean> first = Constant.stages();
        Map<Integer, StageBean> second = Constant.stages();

        Assertions.assertSame(first, second, "重复取用必须命中同一份缓存");
        Assertions.assertTrue(Constant.stageLoadAttempts() <= 1,
                "stage.json 最多解析一次，实际 " + Constant.stageLoadAttempts() + " 次");
    }

    /**
     * 核心护栏：{@link Constant} 的**静态初始化块**里不该出现关卡表。
     *
     * <p>为什么必须只切静态块、不能扫整个类：{@code stages()} 方法体里本来就会出现
     * {@code Constant$StageHolder.LOADED}（那是懒加载的读取口），扫整个类必然误报。
     *
     * <p>做法：反汇编 {@code Constant.class}，从 {@code static {}} 行开始，
     * 收集到下一个成员声明为止，断言这段里既没有 {@code stage.json}
     * 也没有 {@code StageHolder}。
     *
     * <p>反向断言 {@code Constant$StageHolder} 里**确实有** {@code stage.json}，
     * 否则本测试会退化成"字符串根本不在项目里"的空断言。
     *
     * <p>与用例顺序无关、与数据文件是否生成也无关（只看编译产物）。
     */
    @Test
    public void staticInitializerDoesNotTouchTheStageTable() {
        String disassembly = disassemble(Constant.class.getName());
        Assumptions.assumeTrue(disassembly != null, "拿不到 javap，跳过字节码级护栏");

        String clinit = sliceStaticInitializer(disassembly);
        Assertions.assertFalse(clinit.isBlank(), "没切到静态块，javap 输出格式变了吗？");
        Assertions.assertFalse(clinit.contains("stage.json"),
                "Constant 的静态块里出现了 stage.json → 懒加载被破坏：\n" + clinit);
        Assertions.assertFalse(clinit.contains("StageHolder"),
                "Constant 的静态块里引用了 StageHolder → 懒加载被破坏：\n" + clinit);
        // 编译器会把 stages()/load() 内联，所以上面两条抓不到"顺手调一下 stages()"。
        // 真正不可能出现在静态块里的是"解析这个文件"本身。
        Assertions.assertFalse(clinit.contains("Method stages:"),
                "Constant 的静态块里调用了 stages() → 懒加载被破坏：\n" + clinit);
        Assertions.assertFalse(clinit.contains("Method load:"),
                "Constant 的静态块里调用了 load() → 懒加载被破坏：\n" + clinit);

        String holder = disassemble("com.laosun.aluminium.Constant$StageHolder");
        Assumptions.assumeTrue(holder != null, "拿不到 StageHolder 的反汇编");
        Assertions.assertTrue(holder.contains("stage.json"),
                "懒加载载体里应当有 stage.json，否则本测试没有意义");
    }

    // ==================================================================

    /** 用当前 JDK 的 javap 反汇编指定类；拿不到就返回 {@code null}（由调用方 skip）。 */
    private static String disassemble(String className) {
        try {
            String javap = System.getProperty("java.home") + "/bin/javap";
            ProcessBuilder builder = new ProcessBuilder(javap, "-p", "-c",
                    "-classpath", System.getProperty("java.class.path"), className);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? output : null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    /**
     * 切出 {@code static {}} 那一段（javap 把静态初始化块标成 {@code static {}}）。
     *
     * <p>javap 的缩进规律：静态块自己顶格写成 {@code static {};}，块体缩进 4 空格，
     * 下一个成员声明顶格（缩进 2 空格，如 {@code public static final ...}）。
     * 所以"遇到下一个缩进 ≤ 2 的非空行"就是块结束。
     */
    private static String sliceStaticInitializer(String disassembly) {
        StringBuilder clinit = new StringBuilder();
        boolean inside = false;
        for (String line : disassembly.lines().toList()) {
            if (line.strip().equals("static {};")) {
                inside = true;
                continue;
            }
            if (inside) {
                int indent = line.length() - line.stripLeading().length();
                if (!line.isBlank() && indent <= 2) {
                    break;                                  // 下一个成员，静态块结束
                }
                clinit.append(line).append('\n');
            }
        }
        return clinit.toString();
    }
}
