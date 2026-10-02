package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNwfmDeviceRuleMapper;
import com.fc.v2.model.auto.TNwfmDeviceRule;
import com.fc.v2.model.custom.DeviceRuleEnrollReceipt;
import com.fc.v2.model.custom.DeviceRuleVerdict;
import com.fc.v2.service.ITNwfmDeviceRuleService;

/**
 * 计量器具口径册 Service（rule-book 形状）。
 *
 * <p>时段比较一律在 Java 侧用毫秒值做，不把 java.util.Date 绑进 SQL：
 * JDBC serverTimezone 与本机时区不对称时，边界时刻会整体偏移，
 * 在「切换那一刻整」（at == effEnd）这类用例上会错档。</p>
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TNwfmDeviceRuleServiceImpl implements ITNwfmDeviceRuleService {

    /** 在册 */
    private static final int STATUS_ON_BOOK = 0;
    /** 撤下（不删行，仍占行次） */
    private static final int STATUS_WITHDRAWN = 1;
    private static final int DEL_FLAG_NORMAL = 0;

    /**
     * 哨兵大数：天花板写成它或更大，按「说不上来的大数」认作空。
     * 现实一档是百分数（个位数撑死两位），到这条线的只可能是占位值。
     */
    private static final BigDecimal ABSURD_LIMIT = new BigDecimal("10000.00");

    /** 行次升序；没排行次的旧行沉底 */
    private static final Comparator<TNwfmDeviceRule> BY_ROW_SEQ = new Comparator<TNwfmDeviceRule>() {
        @Override
        public int compare(TNwfmDeviceRule a, TNwfmDeviceRule b) {
            Integer sa = a.getRowSeq();
            Integer sb = b.getRowSeq();
            if (sa == null && sb == null) {
                return 0;
            }
            if (sa == null) {
                return 1;
            }
            if (sb == null) {
                return -1;
            }
            return sa.compareTo(sb);
        }
    };

    @javax.annotation.Resource
    private TNwfmDeviceRuleMapper nwfmDeviceRuleMapper;

    @Override
    public TNwfmDeviceRule selectTNwfmDeviceRuleById(Long id) {
        if (id == null) {
            return null;
        }
        return this.nwfmDeviceRuleMapper.selectById(id);
    }

    @Override
    public List<TNwfmDeviceRule> listBook() {
        List<TNwfmDeviceRule> all = activeRows();
        Collections.sort(all, BY_ROW_SEQ);
        return all;
    }

    @Override
    public DeviceRuleVerdict judge(String itemName, BigDecimal deviation, Date at) {
        if (deviation == null || deviation.compareTo(BigDecimal.ZERO) < 0) {
            return DeviceRuleVerdict.illegalInput("示值偏差为空或为负，不入册判");
        }
        Date moment = at == null ? new Date() : at;
        if (itemName == null || itemName.trim().isEmpty()) {
            // 名目空：没有任何一行认这件事（空名目的行也不认它）
            return DeviceRuleVerdict.notInBook("名目为空，册上无此一段");
        }
        String matter = itemName.trim();

        List<TNwfmDeviceRule> sameMatter = new ArrayList<TNwfmDeviceRule>();
        for (TNwfmDeviceRule r : activeRows()) {
            if (matter.equals(r.getItemName())) {
                sameMatter.add(r);
            }
        }
        if (sameMatter.isEmpty()) {
            // 口径四：册上压根没有认这一段的口径
            return DeviceRuleVerdict.notInBook("册上没有名目「" + matter + "」的行");
        }

        List<TNwfmDeviceRule> effective = new ArrayList<TNwfmDeviceRule>();
        for (TNwfmDeviceRule r : sameMatter) {
            if (isOnBook(r) && covers(r, moment)) {
                effective.add(r);
            }
        }
        if (effective.isEmpty()) {
            // 口径四：认的那几行全被撤下 / 时段盖不住这一刻
            return DeviceRuleVerdict.unresolved(
                    "名目「" + matter + "」在 " + fmt(moment) + " 无在册管用行", sameMatter);
        }

        // 口径二：更新时刻晚者说话；一样晚，代号字面升序
        TNwfmDeviceRule chosen = effective.size() == 1 ? effective.get(0)
                : Collections.min(effective, EFFECTIVE_PICK);

        Integer level = levelOf(chosen, deviation);
        if (level == null) {
            // 口径六：差得过分，越过最高一档的非空天花板，走人工，不给段
            return DeviceRuleVerdict.manual(chosen,
                    "示值偏差 " + deviation.toPlainString() + " 越过停用档非空天花板，走人工",
                    effective);
        }
        return DeviceRuleVerdict.ok(level, chosen, effective);
    }

    @Override
    public List<TNwfmDeviceRule> searchByItemName(String keyword) {
        if (keyword == null) {
            return new ArrayList<TNwfmDeviceRule>();
        }
        String word = keyword.trim();
        if (word.isEmpty()) {
            // 口径五：空词一条都不列（空着不等于「什么都算」）
            return new ArrayList<TNwfmDeviceRule>();
        }
        // 逐字全等：走 = 绑定，不用 LIKE；item_name 为 NULL 的行自然不命中
        List<TNwfmDeviceRule> hit = this.nwfmDeviceRuleMapper.selectList(
                new QueryWrapper<TNwfmDeviceRule>()
                        .eq("del_flag", DEL_FLAG_NORMAL)
                        .eq("item_name", word));
        Collections.sort(hit, BY_ROW_SEQ);
        return hit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceRuleEnrollReceipt enroll(TNwfmDeviceRule row) {
        if (row == null) {
            return DeviceRuleEnrollReceipt.reject(DeviceRuleEnrollReceipt.Status.ILLEGAL_ROW,
                    "没有行可录");
        }
        if (isBlank(row.getRuleCode())) {
            return DeviceRuleEnrollReceipt.reject(DeviceRuleEnrollReceipt.Status.ILLEGAL_ROW,
                    "代号空着，不许录");
        }
        if (isBlank(row.getItemName())) {
            return DeviceRuleEnrollReceipt.reject(DeviceRuleEnrollReceipt.Status.ILLEGAL_ROW,
                    "名目空着，此行不认得任何一件事，不许录");
        }
        // 口径一：行次系统在上头排，手填无效
        row.setRowSeq(null);
        row.setRuleCode(row.getRuleCode().trim());
        row.setItemName(row.getItemName().trim());
        sanitizeLimits(row);

        List<TNwfmDeviceRule> all = activeRows();

        // 同代号：在册行已在 → 退回；只有撤下行 → 补回，沿用原行次
        TNwfmDeviceRule sameCodeOn = null;
        TNwfmDeviceRule sameCodeOff = null;
        for (TNwfmDeviceRule r : all) {
            if (!row.getRuleCode().equals(r.getRuleCode())) {
                continue;
            }
            if (isOnBook(r)) {
                sameCodeOn = r;
            } else {
                sameCodeOff = r;
            }
        }
        if (sameCodeOn != null) {
            return DeviceRuleEnrollReceipt.reject(DeviceRuleEnrollReceipt.Status.DUPLICATE_ACTIVE,
                    "代号 " + row.getRuleCode() + " 的在册行已在，第二行退回");
        }
        if (sameCodeOff != null) {
            restoreOnto(sameCodeOff, row);
            this.nwfmDeviceRuleMapper.updateById(sameCodeOff);
            return DeviceRuleEnrollReceipt.restored(sameCodeOff,
                    "沿用原行次 " + sameCodeOff.getRowSeq() + " 补回");
        }

        // 口径一：同一件事（同名目）下，时段相交的在册行只容一行；
        // 半开区间端点相接（旧版 effEnd == 新版 effStart）不算相交。
        for (TNwfmDeviceRule r : all) {
            if (isOnBook(r)
                    && row.getItemName().equals(r.getItemName())
                    && windowsOverlap(row, r)) {
                return DeviceRuleEnrollReceipt.reject(DeviceRuleEnrollReceipt.Status.DUPLICATE_MATTER,
                        "名目「" + row.getItemName() + "」该时段已有在册行（行次 "
                                + r.getRowSeq() + "），一件事只容一行，退回");
            }
        }

        int nextSeq = 1;
        for (TNwfmDeviceRule r : all) {
            if (r.getRowSeq() != null && r.getRowSeq() >= nextSeq) {
                nextSeq = r.getRowSeq() + 1;
            }
        }
        row.setRowSeq(nextSeq);
        row.setStatus(STATUS_ON_BOOK);
        row.setDelFlag(DEL_FLAG_NORMAL);
        this.nwfmDeviceRuleMapper.insert(row);
        return DeviceRuleEnrollReceipt.accepted(row, "排定行次 " + nextSeq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean withdraw(Long id) {
        if (id == null) {
            return false;
        }
        TNwfmDeviceRule r = this.nwfmDeviceRuleMapper.selectById(id);
        // 口径七：撤下不删行，往日册面行一条不减
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != DEL_FLAG_NORMAL)) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() == STATUS_WITHDRAWN) {
            return true;
        }
        r.setStatus(STATUS_WITHDRAWN);
        return this.nwfmDeviceRuleMapper.updateById(r) > 0;
    }

    // ------------------------------------------------------------------

    /** 全册：未删行（含撤下） */
    private List<TNwfmDeviceRule> activeRows() {
        return this.nwfmDeviceRuleMapper.selectList(
                new QueryWrapper<TNwfmDeviceRule>().eq("del_flag", DEL_FLAG_NORMAL));
    }

    private boolean isOnBook(TNwfmDeviceRule r) {
        return r.getStatus() == null || r.getStatus() == STATUS_ON_BOOK;
    }

    /** 口径二/三：[effStart, effEnd)；两端皆可空（从头管起 / 管到永远） */
    private boolean covers(TNwfmDeviceRule r, Date moment) {
        long t = moment.getTime();
        if (r.getEffStart() != null && t < r.getEffStart().getTime()) {
            return false;
        }
        if (r.getEffEnd() != null && t >= r.getEffEnd().getTime()) {
            return false;
        }
        return true;
    }

    /** 半开区间 [s,e) 相交；端点相接不算 */
    private boolean windowsOverlap(TNwfmDeviceRule a, TNwfmDeviceRule b) {
        long as = a.getEffStart() == null ? Long.MIN_VALUE : a.getEffStart().getTime();
        long ae = a.getEffEnd() == null ? Long.MAX_VALUE : a.getEffEnd().getTime();
        long bs = b.getEffStart() == null ? Long.MIN_VALUE : b.getEffStart().getTime();
        long be = b.getEffEnd() == null ? Long.MAX_VALUE : b.getEffEnd().getTime();
        return as < be && bs < ae;
    }

    /**
     * 口径六：空着 / 负数 / 哨兵大数都按「此档无天花板」认（null）。
     * 不当零，也不当比谁都大。
     */
    private BigDecimal ceiling(TNwfmDeviceRule r, int tier) {
        BigDecimal raw;
        if (tier == 1) {
            raw = r.getTh1Max();
        } else if (tier == 2) {
            raw = r.getTh2Max();
        } else {
            raw = r.getTh3Max();
        }
        if (raw == null || raw.compareTo(BigDecimal.ZERO) < 0
                || raw.compareTo(ABSURD_LIMIT) >= 0) {
            return null;
        }
        return raw;
    }

    /**
     * 从低到高落档：第一道「天花板为空，或偏差不高于天花板」的档接住。
     * 天花板空 = 这一段没有天花板，差得再多也算在里头。
     * 三道天花板都非空还越过最高一道 → null（走人工，系统不给段）。
     * 边界值落在天花板上，归这一段（天花板盖住本段）。
     */
    private Integer levelOf(TNwfmDeviceRule r, BigDecimal deviation) {
        for (int tier = 1; tier <= 3; tier++) {
            BigDecimal ceil = ceiling(r, tier);
            if (ceil == null || deviation.compareTo(ceil) <= 0) {
                return tier;
            }
        }
        return null;
    }

    /** 撤下行补回：旧壳沿用（行次、代号不动），内里换成新补这一版 */
    private void restoreOnto(TNwfmDeviceRule oldRow, TNwfmDeviceRule fresh) {
        oldRow.setRuleName(fresh.getRuleName());
        oldRow.setItemName(fresh.getItemName());
        oldRow.setTh1Max(fresh.getTh1Max());
        oldRow.setTh2Max(fresh.getTh2Max());
        oldRow.setTh3Max(fresh.getTh3Max());
        oldRow.setEffStart(fresh.getEffStart());
        oldRow.setEffEnd(fresh.getEffEnd());
        oldRow.setRemark(fresh.getRemark());
        oldRow.setStatus(STATUS_ON_BOOK);
        // rowSeq 不动：认的还是当初那一个行次
    }

    /** 录入即按口径六洗净负数/哨兵，库存口径与判定口径保持一致 */
    private void sanitizeLimits(TNwfmDeviceRule r) {
        r.setTh1Max(clean(r.getTh1Max()));
        r.setTh2Max(clean(r.getTh2Max()));
        r.setTh3Max(clean(r.getTh3Max()));
    }

    private BigDecimal clean(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) < 0
                || v.compareTo(ABSURD_LIMIT) >= 0) {
            return null;
        }
        return v;
    }

    /** 口径二裁决：更新时刻晚者胜（null 当最早）；平则代号字面升序（null 沉底） */
    private static final Comparator<TNwfmDeviceRule> EFFECTIVE_PICK = new Comparator<TNwfmDeviceRule>() {
        @Override
        public int compare(TNwfmDeviceRule a, TNwfmDeviceRule b) {
            Date ua = a.getUpdateTime() != null ? a.getUpdateTime() : a.getCreateTime();
            Date ub = b.getUpdateTime() != null ? b.getUpdateTime() : b.getCreateTime();
            if (ua == null && ub != null) {
                return 1;
            }
            if (ua != null && ub == null) {
                return -1;
            }
            if (ua != null && ub != null) {
                int c = ub.compareTo(ua);
                if (c != 0) {
                    return c;
                }
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            // 字面次序，不是数字大小：'B' 在 'A' 之后
            return ca.compareTo(cb);
        }
    };

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String fmt(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}
