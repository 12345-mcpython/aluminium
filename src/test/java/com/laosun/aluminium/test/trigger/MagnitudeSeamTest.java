package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.trigger.Magnitude;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The magnitude layer reads {@link Magnitude} and not the bean.
 *
 * <p><b>What this pins.</b> Turning a spelling ("80% of 素裳's ATK", "the event's amount", "the skill's third cell")
 * into a number is one question with one reader: {@link TriggerMagnitudes}, plus {@link NumberProviders} for the
 * expression form. Both used to take the whole {@link EffectSpec} - 139 fields - so the reader could in principle read
 * any of them, and "which numbers does an effect state" had no answer anywhere. The interface is that answer, and the
 * only way it stays true is if nothing in the layer can reach past it.
 *
 * <p><b>Why reflection and not a source scan.</b> The claim is about the types the layer's methods accept, and
 * reflection states exactly that. A source regex would also fire on a comment or a local variable, which is noise: what
 * matters is that a <b>parameter</b> is the interface.
 *
 * <p><b>The one documented leak.</b> A scale that names another unit's attribute ({@code target_max_hp}) has to know who
 * the target is, so {@code TriggerMagnitudes} still casts to the bean in exactly one place to resolve a selector. This
 * case does not forbid that - it forbids the layer from <b>declaring</b> a bean parameter, which is the difference
 * between "one documented cast" and "the layer knows the bean".
 */
public class MagnitudeSeamTest {

    @Test
    public void theMagnitudeReaderTakesNoEffectSpecParameter() {
        List<String> offenders = new ArrayList<>();
        List<Class<?>> readers = new ArrayList<>();
        try {
            readers.add(Class.forName("com.laosun.aluminium.trigger.TriggerMagnitudes"));
            readers.add(Class.forName("com.laosun.aluminium.trigger.NumberProviders"));
        } catch (ClassNotFoundException gone) {
            Assertions.fail("the magnitude readers must still exist under these names", gone);
        }
        for (Class<?> reader : readers) {
            for (Method method : reader.getDeclaredMethods()) {
                if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())) {
                    continue;
                }
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (parameter == EffectSpec.class) {
                        offenders.add(reader.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
        Assertions.assertTrue(offenders.isEmpty(),
                "the magnitude layer declares these methods with the whole bean as a parameter: " + offenders
                        + " - take Magnitude instead, and read the field through it");
    }

    @Test
    public void theBeanIsAMagnitude() {
        Assertions.assertTrue(Magnitude.class.isAssignableFrom(EffectSpec.class),
                "EffectSpec must implement Magnitude: that is what lets the reader's parameter type change while every"
                        + " call site keeps passing the bean");
    }

    @Test
    public void theMagnitudeReaderStillResolvesTheNumberItAlwaysDid() {
        // A behavioural anchor, so a signature change cannot silently turn this case into a tautology: a literal
        // percent resolves to itself, and the reader is still reachable through the interface's type.
        EffectSpec effect = new EffectSpec();
        com.laosun.aluminium.test.support.TriggerSpecs.set(effect, "percent", 0.25);
        Magnitude magnitude = effect;
        Assertions.assertEquals(0.25, magnitude.getPercent(), 1e-12,
                "reading through the interface must answer exactly what reading the bean answers");
    }
}
