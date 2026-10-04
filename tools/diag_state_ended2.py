"""Last discriminator: drop the `self state_ended <name>` condition entirely.

If the recorder then sees 3, the CONDITION is what fails for a hand-built rule (the shipped 1211 file uses the same
spelling successfully). If it still sees 0, the event is not reaching the rule at all and the cause is in the firing.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/StateEndedAmountTest.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = '''TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STATE), record, fires)'''
NEW = '''TriggerSpecs.rule("STATE_ENDED", List.of(), record, fires)'''
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: anchor")
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   condition dropped for the discriminator")
