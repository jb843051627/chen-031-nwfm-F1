package com.fc.v2.model.custom;

import java.util.List;

import com.fc.v2.model.auto.TNwfmDeviceRule;

/**
 * 口径册判定结论（一条偏差在某一刻认段的结果）。
 *
 * <p>结局分五种：</p>
 * <ul>
 *   <li>{@link Outcome#OK} 认了段，level 取 1 准秤 / 2 复称 / 3 停用；</li>
 *   <li>{@link Outcome#NOT_IN_BOOK} 段不在册：册上压根没有认这一名目的行；</li>
 *   <li>{@link Outcome#UNRESOLVED} 认不出：有认这一名目的行，但此刻没有一行在册管用
 *       （全被撤下，或生效时段都盖不住这一刻）；</li>
 *   <li>{@link Outcome#MANUAL} 差得过分、越过最高一档的非空天花板：走人工，系统不给段；</li>
 *   <li>{@link Outcome#ILLEGAL_INPUT} 入参不合法（偏差为空或为负）。</li>
 * </ul>
 *
 * <p>认不出与段不在册是两条不同的退回：挑不出就是挑不出，这一趟不落任何一段，
 * 不许顺手挑一段挂上充数。</p>
 *
 * @author fuce
 * @date 2026-10-02
 */
public class DeviceRuleVerdict {

    public enum Outcome {
        /** 认了段 */
        OK,
        /** 段不在册：册上没有认这一名目的行 */
        NOT_IN_BOOK,
        /** 认不出：有这一名目的行，但此刻无在册管用行 */
        UNRESOLVED,
        /** 差得过分，走人工 */
        MANUAL,
        /** 入参不合法 */
        ILLEGAL_INPUT
    }

    /** 处置档：1 准秤 / 2 复称 / 3 停用；未认段时为 null */
    public static final int LEVEL_ACCURATE = 1;
    public static final int LEVEL_RECHECK = 2;
    public static final int LEVEL_SUSPEND = 3;

    private final Outcome outcome;
    private final Integer level;
    private final TNwfmDeviceRule rule;
    private final String reason;
    /** 认这一名目的在册/在册候选行（退回时备查，可能为空，永不为 null） */
    private final List<TNwfmDeviceRule> candidates;

    private DeviceRuleVerdict(Outcome outcome, Integer level, TNwfmDeviceRule rule,
                              String reason, List<TNwfmDeviceRule> candidates) {
        this.outcome = outcome;
        this.level = level;
        this.rule = rule;
        this.reason = reason;
        this.candidates = candidates == null
                ? new java.util.ArrayList<TNwfmDeviceRule>() : candidates;
    }

    public static DeviceRuleVerdict ok(int level, TNwfmDeviceRule rule,
                                       List<TNwfmDeviceRule> candidates) {
        return new DeviceRuleVerdict(Outcome.OK, level, rule, null, candidates);
    }

    public static DeviceRuleVerdict notInBook(String reason) {
        return new DeviceRuleVerdict(Outcome.NOT_IN_BOOK, null, null, reason, null);
    }

    public static DeviceRuleVerdict unresolved(String reason, List<TNwfmDeviceRule> candidates) {
        return new DeviceRuleVerdict(Outcome.UNRESOLVED, null, null, reason, candidates);
    }

    public static DeviceRuleVerdict manual(TNwfmDeviceRule rule, String reason,
                                           List<TNwfmDeviceRule> candidates) {
        return new DeviceRuleVerdict(Outcome.MANUAL, null, rule, reason, candidates);
    }

    public static DeviceRuleVerdict illegalInput(String reason) {
        return new DeviceRuleVerdict(Outcome.ILLEGAL_INPUT, null, null, reason, null);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Integer getLevel() {
        return level;
    }

    public TNwfmDeviceRule getRule() {
        return rule;
    }

    public String getReason() {
        return reason;
    }

    public List<TNwfmDeviceRule> getCandidates() {
        return candidates;
    }
}
