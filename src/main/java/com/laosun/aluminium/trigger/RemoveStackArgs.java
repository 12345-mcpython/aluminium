package com.laosun.aluminium.trigger;

/**
 * What {@link RemoveStackOp} parsed out of its effect.
 *
 * <p>{@code buff} OR {@code attribute} names the thing, exactly one: "remove 1 stack of [鸣弦号令]" is about a named
 * stackable modifier, which the attribute form cannot address.
 *
 * @param magnitude how many layers to take off
 * @param buff      the named stackable modifier, or {@code null}
 * @param attribute the attribute whose stacks to take off, or {@code null}
 */
record RemoveStackArgs(Magnitude magnitude, String buff, String attribute) implements OpArgs {
}
