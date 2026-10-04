"""Discriminator for the STATE_ENDED judge: is the rule firing at all, and is the amount zero?

Both effects live in ONE rule, because same-event rules only fire once (measured last round). A constant 1 into one
resource, the event's amount into another: fires=1 with amount=0 means the rule ran but the amount did not travel;
fires=0 means the rule never matched.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/StateEndedAmountTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = '''        EffectSpec record = effect("GAIN_RESOURCE", "resource", RECORD, "amountFromEvent", Boolean.TRUE);
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), stack(), stack(), stack()),
                        TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STATE), record)),
                List.of(new ResourceSpec(RECORD, 2147483647, 0, null, null, "hand-built probe", null)));'''
NEW = '''        EffectSpec record = effect("GAIN_RESOURCE", "resource", RECORD, "amountFromEvent", Boolean.TRUE);
        EffectSpec fires = effect("GAIN_RESOURCE", "resource", FIRES, "amount", 1.0);
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), stack(), stack(), stack()),
                        // one rule, two effects: same-event rules only fire once (measured), and the pair tells
                        // "the rule never matched" apart from "the amount did not travel"
                        TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STATE), record, fires)),
                List.of(new ResourceSpec(RECORD, 2147483647, 0, null, null, "hand-built probe", null),
                        new ResourceSpec(FIRES, 2147483647, 0, null, null, "hand-built probe", null)));'''
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: anchor 1")
text = text.replace(OLD, NEW, 1)

OLD2 = '''    private static final String RECORD = "probeRecord";'''
NEW2 = '''    private static final String RECORD = "probeRecord";
    private static final String FIRES = "probeFires";'''
if text.count(OLD2) != 1:
    raise SystemExit("REFUSING: anchor 2")
text = text.replace(OLD2, NEW2, 1)

OLD3 = '''        System.out.println("[STATE_ENDED] removed=" + removed + " recorded=" + recorded);'''
NEW3 = '''        System.out.println("[STATE_ENDED] removed=" + removed + " recorded=" + recorded
                + " fires=" + owner.getResources().value(FIRES));'''
if text.count(OLD3) != 1:
    raise SystemExit("REFUSING: anchor 3")
text = text.replace(OLD3, NEW3, 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   discriminator added")
