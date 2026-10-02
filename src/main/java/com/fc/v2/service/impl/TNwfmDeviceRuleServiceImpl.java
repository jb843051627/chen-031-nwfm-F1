package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNwfmDeviceRuleMapper;
import com.fc.v2.model.auto.TNwfmDeviceRule;
import com.fc.v2.model.custom.NwfmRuleEvalResult;
import com.fc.v2.service.ITNwfmDeviceRuleService;

/**
 * 计量器具判定规则 Service业务层处理（rule-eval 形状：求值为主，兼册面登记）。
 * 各条口径的落法见接口 {@link ITNwfmDeviceRuleService} 的逐条注释。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TNwfmDeviceRuleServiceImpl implements ITNwfmDeviceRuleService {

    /**
     * 上限列 decimal(8,2) 能写下的最大数。写成负数、或达到/超过这个说不上的大数，
     * 一概按「空着」认（口径六）——不当成零，也不当成比谁都大。
     */
    private static final BigDecimal UNBOUNDED_MIN = new BigDecimal("999999.99");

    private static final Date EPOCH = new Date(0L);

    /**
     * 认行次序（口径二）：改得晚的在前；改得一样晚按代号字面升序
     * （String.compareTo 是字面次序不是数字大小，'NR10' 排在 'NR9' 前头）；
     * 再一样按行次（id）升序，册面从上往下认。
     */
    private static final Comparator<TNwfmDeviceRule> RECOGNITION_ORDER = new Comparator<TNwfmDeviceRule>() {
        @Override
        public int compare(TNwfmDeviceRule a, TNwfmDeviceRule b) {
            int byModified = modifiedOf(b).compareTo(modifiedOf(a));
            if (byModified != 0) {
                return byModified;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int byCode = ca.compareTo(cb);
            if (byCode != 0) {
                return byCode;
            }
            return Long.compare(idOf(a), idOf(b));
        }
    };

    /** 行次（id）升序：册面一行一行往下排（口径一） */
    private static final Comparator<TNwfmDeviceRule> ROW_ORDER = new Comparator<TNwfmDeviceRule>() {
        @Override
        public int compare(TNwfmDeviceRule a, TNwfmDeviceRule b) {
            return Long.compare(idOf(a), idOf(b));
        }
    };

    @javax.annotation.Resource
    private TNwfmDeviceRuleMapper nwfmDeviceRuleMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static long idOf(TNwfmDeviceRule r) {
        return r.getId() == null ? Long.MAX_VALUE : r.getId().longValue();
    }

    /** 去首尾空白：半角、全角空白都不算词的一部分（口径五） */
    private static String trimBlank(String s) {
        if (s == null) {
            return "";
        }
        int b = 0;
        int e = s.length();
        while (b < e && isBlankChar(s.charAt(b))) {
            b++;
        }
        while (e > b && isBlankChar(s.charAt(e - 1))) {
            e--;
        }
        return s.substring(b, e);
    }

    private static boolean isBlankChar(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    /** 改得晚＝update_time；一行从没改过（update_time 空）取其落册那一刻 create_time（口径二） */
    private static Date modifiedOf(TNwfmDeviceRule r) {
        if (r.getUpdateTime() != null) {
            return r.getUpdateTime();
        }
        if (r.getCreateTime() != null) {
            return r.getCreateTime();
        }
        return EPOCH;
    }

    /** 在用行：未删除（del_flag=0）且未撤下（status=0）；格子空着按在用认 */
    private static boolean isLive(TNwfmDeviceRule r) {
        boolean notDeleted = r.getDelFlag() == null || r.getDelFlag().intValue() == 0;
        boolean notWithdrawn = r.getStatus() == null || r.getStatus().intValue() == 0;
        return notDeleted && notWithdrawn;
    }

    /** 与 {@link #isLive} 同义的 SQL 条件（纯 SQL 判定时用） */
    private static void appendLive(QueryWrapper<TNwfmDeviceRule> qw) {
        qw.and(w -> w.isNull("del_flag").or().eq("del_flag", 0));
        qw.and(w -> w.isNull("status").or().eq("status", 0));
    }

    /**
     * at 时刻落在管段内的 SQL 条件（口径三：eff_start 含、eff_end 不含——
     * 到头那一刻起自己就不管用了）。一律墙钟字符串绑定，见 {@link #ts}。
     */
    private static void appendValidAt(QueryWrapper<TNwfmDeviceRule> qw, Date at) {
        String wall = ts(at);
        qw.and(w -> w.isNull("eff_start").or().le("eff_start", wall));
        qw.and(w -> w.isNull("eff_end").or().gt("eff_end", wall));
    }

    @Override
    public TNwfmDeviceRule selectTNwfmDeviceRuleById(Long id) {
        return this.nwfmDeviceRuleMapper.selectById(id);
    }

    @Override
    public List<TNwfmDeviceRule> listAvailable(Date at) {
        List<TNwfmDeviceRule> rows = new ArrayList<TNwfmDeviceRule>();
        if (at == null) {
            return rows;
        }
        QueryWrapper<TNwfmDeviceRule> qw = new QueryWrapper<TNwfmDeviceRule>();
        appendLive(qw);
        appendValidAt(qw, at);
        List<TNwfmDeviceRule> hit = this.nwfmDeviceRuleMapper.selectList(qw);
        if (hit != null) {
            rows.addAll(hit);
        }
        rows.sort(RECOGNITION_ORDER);
        return rows;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        return evaluateDetail(ruleCode, input, at).getLevel();
    }

    @Override
    public NwfmRuleEvalResult evaluateDetail(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || trimBlank(ruleCode).isEmpty() || input == null || at == null) {
            return NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_INVALID);
        }
        if (input.compareTo(BigDecimal.ZERO) < 0) {
            // 示值偏差没有负的，负输入按参数无效退回
            return NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_INVALID);
        }
        List<TNwfmDeviceRule> same = new ArrayList<TNwfmDeviceRule>();
        for (TNwfmDeviceRule r : listAvailable(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                same.add(r);
            }
        }
        if (!same.isEmpty()) {
            same.sort(RECOGNITION_ORDER);
            TNwfmDeviceRule winner = same.get(0);
            return NwfmRuleEvalResult.ok(levelOf(winner, input), winner);
        }
        // 一段口径都认不出来（口径四）：册上有没有认这一件事的行，决定写哪一种退回
        return existsAnyRowOfCode(ruleCode)
                ? NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_UNRECOGNIZABLE)
                : NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_NOT_IN_REGISTRY);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return evaluateTopDetail(input, at).getLevel();
    }

    @Override
    public NwfmRuleEvalResult evaluateTopDetail(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_INVALID);
        }
        if (input.compareTo(BigDecimal.ZERO) < 0) {
            return NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_INVALID);
        }
        List<TNwfmDeviceRule> candidates = listAvailable(at);
        if (!candidates.isEmpty()) {
            TNwfmDeviceRule winner = candidates.get(0);
            return NwfmRuleEvalResult.ok(levelOf(winner, input), winner);
        }
        // 册上空空如也是「段不在册」；有行但全被撤下/不在管段是「认不出」（口径四）
        Integer total = this.nwfmDeviceRuleMapper.selectCount(new QueryWrapper<TNwfmDeviceRule>());
        boolean registryEmpty = total == null || total.intValue() == 0;
        return registryEmpty
                ? NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_NOT_IN_REGISTRY)
                : NwfmRuleEvalResult.reject(NwfmRuleEvalResult.REASON_UNRECOGNIZABLE);
    }

    @Override
    public List<TNwfmDeviceRule> listByName(String keyword) {
        List<TNwfmDeviceRule> hits = new ArrayList<TNwfmDeviceRule>();
        if (keyword == null) {
            return hits;
        }
        // 关键词前头后头多出来的空白不算词的一部分（口径五）
        String key = trimBlank(keyword);
        if (key.isEmpty()) {
            return hits;
        }
        QueryWrapper<TNwfmDeviceRule> qw = new QueryWrapper<TNwfmDeviceRule>();
        appendLive(qw);
        List<TNwfmDeviceRule> rows = this.nwfmDeviceRuleMapper.selectList(qw);
        if (rows != null) {
            for (TNwfmDeviceRule r : rows) {
                // 逐字全等：只对上整段才算对上，上半截不列；名目空着的一律不列；
                // 相近的名目不并——Java 逐字比，绕开库 collation 的大小写/尾空格宽容
                if (key.equals(r.getRuleName())) {
                    hits.add(r);
                }
            }
        }
        hits.sort(ROW_ORDER);
        return hits;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        QueryWrapper<TNwfmDeviceRule> qw = new QueryWrapper<TNwfmDeviceRule>();
        qw.eq("id", id);
        appendLive(qw);
        appendValidAt(qw, at);
        Integer c = this.nwfmDeviceRuleMapper.selectCount(qw);
        return c != null && c.intValue() > 0;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    @Override
    public int addRule(TNwfmDeviceRule rule) {
        if (rule == null || trimBlank(rule.getRuleCode()).isEmpty()) {
            return 0;
        }
        String code = rule.getRuleCode();
        List<TNwfmDeviceRule> rows = this.nwfmDeviceRuleMapper.selectList(
                new QueryWrapper<TNwfmDeviceRule>().eq("rule_code", code));
        List<TNwfmDeviceRule> sameCode = new ArrayList<TNwfmDeviceRule>();
        if (rows != null) {
            for (TNwfmDeviceRule r : rows) {
                if (code.equals(r.getRuleCode())) {
                    sameCode.add(r);
                }
            }
        }
        // 同一册里一件事只容一行在用：已有在用行，第二行退回，不按新行算数（口径一）
        for (TNwfmDeviceRule r : sameCode) {
            if (isLive(r)) {
                return 0;
            }
        }
        // 退了的行再补上来：认的还是当初那一个行次——在行次最小（当初）的那一行上补回（口径一）
        TNwfmDeviceRule revive = null;
        for (TNwfmDeviceRule r : sameCode) {
            if (revive == null || idOf(r) < idOf(revive)) {
                revive = r;
            }
        }
        if (revive != null) {
            revive.setRuleName(rule.getRuleName());
            revive.setTh1Max(rule.getTh1Max());
            revive.setTh2Max(rule.getTh2Max());
            revive.setTh3Max(rule.getTh3Max());
            revive.setEffStart(rule.getEffStart());
            revive.setEffEnd(rule.getEffEnd());
            revive.setPriority(rule.getPriority());
            revive.setRemark(rule.getRemark());
            revive.setStatus(Integer.valueOf(0));
            revive.setDelFlag(Integer.valueOf(0));
            revive.setUpdateTime(new Date());
            this.nwfmDeviceRuleMapper.updateById(revive);
            return 1;
        }
        // 新行：行次由系统在上头排出来，谁也不许自己敲进去（口径一）
        rule.setId(null);
        rule.setDelFlag(Integer.valueOf(0));
        if (rule.getStatus() == null) {
            rule.setStatus(Integer.valueOf(0));
        }
        if (rule.getCreateTime() == null) {
            rule.setCreateTime(new Date());
        }
        this.nwfmDeviceRuleMapper.insert(rule);
        return 1;
    }

    @Override
    public int withdrawRule(Long id) {
        if (id == null) {
            return 0;
        }
        TNwfmDeviceRule r = this.nwfmDeviceRuleMapper.selectById(id);
        if (r == null) {
            return 0;
        }
        // 撤下：行还留在册面上（口径七，往日册面行一条不减），只是不再认事
        r.setStatus(Integer.valueOf(1));
        r.setUpdateTime(new Date());
        return this.nwfmDeviceRuleMapper.updateById(r) > 0 ? 1 : 0;
    }

    /** 册上有没有认这一件事的行（不论在用、撤下、删除），逐字认代号 */
    private boolean existsAnyRowOfCode(String ruleCode) {
        List<TNwfmDeviceRule> rows = this.nwfmDeviceRuleMapper.selectList(
                new QueryWrapper<TNwfmDeviceRule>().eq("rule_code", ruleCode));
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (TNwfmDeviceRule r : rows) {
            if (ruleCode.equals(r.getRuleCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 落段（口径六）：格子上写的是上一段的天花板，上限本身含在本段内；
     * 上限空着＝这一段没有天花板，差得再多也算在里头；
     * 越过所有有界上限 → 4 走人工，系统自己不给这一段落数。
     */
    private static int levelOf(TNwfmDeviceRule rule, BigDecimal input) {
        BigDecimal t1 = normCeiling(rule.getTh1Max());
        BigDecimal t2 = normCeiling(rule.getTh2Max());
        BigDecimal t3 = normCeiling(rule.getTh3Max());
        if (t1 == null || input.compareTo(t1) <= 0) {
            return NwfmRuleEvalResult.LEVEL_ACCURATE;
        }
        if (t2 == null || input.compareTo(t2) <= 0) {
            return NwfmRuleEvalResult.LEVEL_RECHECK;
        }
        if (t3 == null || input.compareTo(t3) <= 0) {
            return NwfmRuleEvalResult.LEVEL_DISABLE;
        }
        return NwfmRuleEvalResult.LEVEL_MANUAL;
    }

    /** 上限归一：空着／负数／说不上的大数一概按空着认（口径六） */
    private static BigDecimal normCeiling(BigDecimal ceiling) {
        if (ceiling == null) {
            return null;
        }
        if (ceiling.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }
        if (ceiling.compareTo(UNBOUNDED_MIN) >= 0) {
            return null;
        }
        return ceiling;
    }
}
