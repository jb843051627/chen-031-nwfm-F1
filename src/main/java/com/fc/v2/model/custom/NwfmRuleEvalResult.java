package com.fc.v2.model.custom;

import java.io.Serializable;

import com.fc.v2.model.auto.TNwfmDeviceRule;

/**
 * 口径求值结果（计量器具判定规则）。
 *
 * 档位含义：
 *   1 准（可用档）／2 复称（复核档）／3 停用（停用档）；
 *   4 人工——差得过分，越过所有有界上限，走人工那一面，系统自己不给这一段落数（口径六）；
 *   0 退回——一段口径都认不出来（口径四），退回必写明原因，且不顺手挑一段挂上去充数，
 *     故 level=0 时 ruleId/ruleCode 一律为空。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class NwfmRuleEvalResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 退回：一段口径都认不出来 */
    public static final int LEVEL_REJECT = 0;
    /** 准：示值偏差落在可用档 */
    public static final int LEVEL_ACCURATE = 1;
    /** 复称：示值偏差落在复核档 */
    public static final int LEVEL_RECHECK = 2;
    /** 停用：示值偏差落在停用档 */
    public static final int LEVEL_DISABLE = 3;
    /** 人工：差得过分，系统不落段 */
    public static final int LEVEL_MANUAL = 4;

    /** 退回原因：册上有认这一段的行，但全被撤下或此刻不在其管段内 */
    public static final String REASON_UNRECOGNIZABLE = "认不出";
    /** 退回原因：册上压根没有认这一段的口径 */
    public static final String REASON_NOT_IN_REGISTRY = "段不在册";
    /** 退回原因：代号/偏差/时刻缺省，或偏差为负 */
    public static final String REASON_INVALID = "参数无效";

    /** 落档：0 退回 / 1 准 / 2 复称 / 3 停用 / 4 人工 */
    private int level;

    /** 退回原因（认不出／段不在册／参数无效）；非退回为 null */
    private String reason;

    /** 认下的那一行的行次；未认下（退回）为 null */
    private Long ruleId;

    /** 认下的那一行的代号；未认下（退回）为 null */
    private String ruleCode;

    public NwfmRuleEvalResult() {
    }

    /** 认下一行并落档（level 1..4） */
    public static NwfmRuleEvalResult ok(int level, TNwfmDeviceRule rule) {
        NwfmRuleEvalResult r = new NwfmRuleEvalResult();
        r.setLevel(level);
        if (rule != null) {
            r.setRuleId(rule.getId());
            r.setRuleCode(rule.getRuleCode());
        }
        return r;
    }

    /** 退回：不落任何一段，也不挂任何一行 */
    public static NwfmRuleEvalResult reject(String reason) {
        NwfmRuleEvalResult r = new NwfmRuleEvalResult();
        r.setLevel(LEVEL_REJECT);
        r.setReason(reason);
        return r;
    }

    /** 是否退回（level=0） */
    public boolean isRejected() {
        return this.level == LEVEL_REJECT;
    }

    /** 是否走人工（level=4） */
    public boolean isManual() {
        return this.level == LEVEL_MANUAL;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }
}
