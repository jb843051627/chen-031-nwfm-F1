package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNwfmDeviceRule;
import com.fc.v2.model.custom.NwfmRuleEvalResult;

/**
 * 计量器具判定规则 Service接口 —— 口径册（rule-eval 形状：求值为主，兼册面登记）。
 *
 * 口径一：册面一行一行往下排，不许插队；行次（id）由系统排出来，谁也不许自己敲进去。
 *         同一册里一件事（rule_code）只容一行在用，第二行退回；退了的行过几天再补上来，
 *         认的还是当初那一个行次（在原行上补回，不新排行次）。
 * 口径二：认哪一行，看它从哪一刻管到哪一刻、管不管这一段；几行都管这一段时，
 *         改得晚（update_time，缺省取 create_time）的那行说话；改得一样晚按代号字面
 *         次序（String 次序，不是数字大小），再一样按行次。认定了一行，别的不吭声。
 * 口径三：eff_end 写的是到头那一刻，到头那一刻起自己就不管用了——[eff_start, eff_end)，
 *         左含右不含，用的时候不许把到头那一刻算进去。
 * 口径四：一段口径都认不出来时这一条退回，并写明是「认不出」（认这一段的行全被撤下
 *         或此刻不在管段内）还是「段不在册」（册上压根没有认这一段的口径）；
 *         退回时不许顺手挑一段挂上去充数。
 * 口径五：按名目（rule_name）检索，关键词首尾空白不算词的一部分，与名目原文逐字
 *         全等才算对上（只对上半截的不列、相近的不并）；名目空着的一律检索不出。
 * 口径六：上限格允许空着，空着＝这一段没有天花板，差得再多也算在里头；写成负数或
 *         说不上的大数（达到列上限 999999.99）一概按空着认，不当成零也不当成比谁
 *         都大。差得过分（越过所有有界上限）走人工，系统不落段（档 4）。
 * 口径七：册上撤行、改小天花板只及往后，先前按老口径出过的结论不追着改——本接口
 *         不落历史结论，求值永远按调用方给的 at 时刻当场认行，天然合规；他日按市场、
 *         摊户把册拆开分面，各分面各自排行次、互不并行，往日册面行一条不减。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITNwfmDeviceRuleService {

    /** 按主键查询规则 */
    TNwfmDeviceRule selectTNwfmDeviceRuleById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻认行并落档。
     * 返回档位 1 准 / 2 复称 / 3 停用 / 4 人工；认不出任何一段（含参数无效）返回 0，
     * 需要退回原因时用 {@link #evaluateDetail}。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 单规则求值（带退回原因）：同 {@link #evaluate}，但退回时写明
     * 「认不出」／「段不在册」／「参数无效」，认下时带回行次与代号（口径四）。
     */
    NwfmRuleEvalResult evaluateDetail(String ruleCode, BigDecimal input, Date at);

    /**
     * 全册求值：at 时刻认下的那一行（口径二次序的队首）落档，语义同 {@link #evaluate}。
     */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 全册求值（带退回原因）：同 {@link #evaluateTop}，退回时写明原因（口径四）。
     */
    NwfmRuleEvalResult evaluateTopDetail(BigDecimal input, Date at);

    /**
     * at 时刻在管段内的在用行，按口径二认行次序排列（队首即全册求值认下的那一行）。
     */
    List<TNwfmDeviceRule> listAvailable(Date at);

    /**
     * 按名目检索（口径五）：关键词去首尾空白后与 rule_name 逐字全等的在用行，
     * 按行次往下排；一个名目认几件事就列几行；名目空着的、只对上半截的一律不列。
     */
    List<TNwfmDeviceRule> listByName(String keyword);

    /** 单行在 at 时刻是否可用（在用且 at 落在其管段内，口径与认行/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用行数（列表页角标 / 看板） */
    int countAvailable(Date at);

    /**
     * 登记一行（口径一）：行次由系统排，入参 id 一律不理会。
     * 同代号已有在用行 → 第二行退回，返回 0；
     * 同代号只有退了的行 → 在当初那一行上补回（行次不变），返回 1；
     * 否则新排一行，返回 1。
     */
    int addRule(TNwfmDeviceRule rule);

    /**
     * 撤下一行：行还留在册面上（口径七，一条不减），只是不再认事。
     * 行不存在返回 0，否则返回 1。
     */
    int withdrawRule(Long id);
}
