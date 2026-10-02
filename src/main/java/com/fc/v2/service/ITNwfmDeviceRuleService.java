package com.fc.v2.service;

import java.math.BigDecimal;

import com.fc.v2.model.auto.TNwfmDeviceRule;
import com.fc.v2.model.custom.DeviceRuleEnrollReceipt;
import com.fc.v2.model.custom.DeviceRuleVerdict;

import java.util.Date;
import java.util.List;

/**
 * 计量器具口径册 Service（rule-book 形状：录行 / 撤行 / 认段 / 按名目检索）。
 *
 * <p>七条口径的落点：</p>
 * <ul>
 *   <li>口径一：行次系统排定（不许手填）；一行退回再补认原行次；同一册、同一名目、
 *       时段相交的在册行只容一行，第二行退回。</li>
 *   <li>口径二：认段先看时段（effStart 含、effEnd 不含），同一名目几行都管用时，
 *       更新时刻晚者说话；一样晚再比代号字面次序（升序，不是数字大小）。</li>
 *   <li>口径三：effEnd 半开，查询时刻正好等于 effEnd 不算在册。</li>
 *   <li>口径四：册上没有这个名目 → 段不在册；有名目但此刻无在册管用行 → 认不出；
 *       两种退回各自标注，绝不凑一段。</li>
 *   <li>口径五：名目逐字全等检索，关键词两侧空白先 trim；名目空着的行一条不列。</li>
 *   <li>口径六：上限空着、写负数、写哨兵大数都按「无天花板」认；越过最高一档的
 *       非空天花板 → 走人工，系统不给段。</li>
 *   <li>口径七：撤行不删行（往日册面行一条不减）；本服务不落历史结论，改册不追旧账。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITNwfmDeviceRuleService {

    /** 按主键查一行（撤下的行也查得到，撤下不删行） */
    TNwfmDeviceRule selectTNwfmDeviceRuleById(Long id);

    /** 整册（未删行，含已撤下），按行次从上往下排 */
    List<TNwfmDeviceRule> listBook();

    /**
     * 认段：某一名目下，at 这一刻的示值偏差该落哪一档（1 准秤 / 2 复称 / 3 停用）。
     * 结局见 {@link DeviceRuleVerdict.Outcome}：OK / 段不在册 / 认不出 / 走人工 / 入参不合法。
     */
    DeviceRuleVerdict judge(String itemName, BigDecimal deviation, Date at);

    /**
     * 按名目检索：关键词 trim 后与名目逐字全等才列（不半截匹配、不按近义词合并）；
     * 名目空着的行不列；关键词空也不列。列出该名目下的每一行（含已撤下），按行次排。
     */
    List<TNwfmDeviceRule> searchByItemName(String keyword);

    /**
     * 录一行：行次由系统排定，rowSeq 手填无效。代号在册上已有的：
     * 撤下行可补回（沿用原行次），在册行退回。同名目时段相交的在册行只容一行，
     * 第二行退回。
     */
    DeviceRuleEnrollReceipt enroll(TNwfmDeviceRule row);

    /** 撤下一行（status 置 1，行与行次都保留）；查无此行返回 false */
    boolean withdraw(Long id);
}
