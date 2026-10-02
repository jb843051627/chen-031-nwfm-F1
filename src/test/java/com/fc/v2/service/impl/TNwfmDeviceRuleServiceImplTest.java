package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNwfmDeviceRuleMapper;
import com.fc.v2.model.auto.TNwfmDeviceRule;
import com.fc.v2.model.custom.DeviceRuleEnrollReceipt;
import com.fc.v2.model.custom.DeviceRuleVerdict;

/**
 * 口径册七条口径的判定单测。mapper 用内存册子冒充，不碰库、不碰时区。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class TNwfmDeviceRuleServiceImplTest {

    // ---------------- 口径二/三：时段半开、更新晚者、字面次序 ----------------

    @Test
    public void halfOpenWindow_boundaryInstantFallsToNewRow() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(1L, "NR01", "衡器示值偏差", 1, "0.50", "2.00", "5.00",
                        "2020-01-01 00:00:00", "2021-01-01 00:00:00", 0, null),
                row(0L, "NR00", "衡器示值偏差", 2, "0.50", "2.00", null,
                        "2021-01-01 00:00:00", "2099-12-31 23:59:59", 0, null));

        DeviceRuleVerdict justBefore = svc.judge("衡器示值偏差", bd("1.00"), d("2020-12-31 23:59:59"));
        assertEquals(DeviceRuleVerdict.Outcome.OK, justBefore.getOutcome());
        assertEquals("NR01", justBefore.getRule().getRuleCode());
        assertEquals(2, justBefore.getLevel());

        // 口径三：到头那一刻自己不管用 → 整点整刻归新行
        DeviceRuleVerdict boundary = svc.judge("衡器示值偏差", bd("1.00"), d("2021-01-01 00:00:00"));
        assertEquals("NR00", boundary.getRule().getRuleCode());

        // 新行在起始那一刻已经管用（起始含）
        DeviceRuleVerdict after = svc.judge("衡器示值偏差", bd("1.00"), d("2021-06-01 00:00:00"));
        assertEquals("NR00", after.getRule().getRuleCode());
    }

    @Test
    public void latestUpdateWins_thenLexicalCodeNotNumeric() {
        // 同一时段两行都在册，更新时刻不同：晚的说话
        TNwfmDeviceRule old = row(10L, "NR10", "衡器示值偏差", 10, "0.50", "2.00", "5.00",
                "2020-01-01 00:00:00", null, 0, d("2026-01-01 00:00:00"));
        TNwfmDeviceRule neu = row(9L, "NR9", "衡器示值偏差", 9, "0.50", "2.00", "5.00",
                "2020-01-01 00:00:00", null, 0, d("2026-05-01 00:00:00"));
        TNwfmDeviceRuleServiceImpl svc = serviceWith(old, neu);

        assertEquals("NR9", svc.judge("衡器示值偏差", bd("0.1"), d("2026-06-01 00:00:00"))
                .getRule().getRuleCode());

        // 更新时刻一样晚：看字面次序，'1'(49) 在 '9'(57) 前，NR10 在前；不是数字 10 > 9
        old.setUpdateTime(d("2026-05-01 00:00:00"));
        assertEquals("NR10", svc.judge("衡器示值偏差", bd("0.1"), d("2026-06-01 00:00:00"))
                .getRule().getRuleCode());
    }

    @Test
    public void withdrawnRowKeepsSilent_butStillOccupiesRowSeq() {
        TNwfmDeviceRule off = row(2L, "NR02", "衡器示值偏差", 3, "0.50", "2.00", "5.00",
                "2020-01-01 00:00:00", null, 1, null);
        TNwfmDeviceRule on = row(0L, "NR00", "衡器示值偏差", 4, "0.50", "2.00", null,
                "2021-01-01 00:00:00", null, 0, null);
        TNwfmDeviceRuleServiceImpl svc = serviceWith(off, on);

        // 撤下行时段全盖也不吭声
        assertEquals("NR00", svc.judge("衡器示值偏差", bd("1"), d("2026-06-01 00:00:00"))
                .getRule().getRuleCode());
        // 撤下不删行：整册还在，行次 3 仍占着，新录行不得给 3（见 enroll 用例）
        List<Integer> seqs = new ArrayList<>();
        for (TNwfmDeviceRule r : svc.listBook()) {
            seqs.add(r.getRowSeq());
        }
        assertTrue(seqs.contains(3));
    }

    // ---------------- 口径四：认不出 / 段不在册，两种退回 ----------------

    @Test
    public void notInBook_whenMatterNeverAppears() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(0L, "NR00", "衡器示值偏差", 1, "0.5", "2", null,
                        "2020-01-01 00:00:00", null, 0, null));
        DeviceRuleVerdict v = svc.judge("册子上没有的名目", bd("1"), d("2026-01-01 00:00:00"));
        assertEquals(DeviceRuleVerdict.Outcome.NOT_IN_BOOK, v.getOutcome());
        assertNull(v.getLevel());
        assertNull(v.getRule());
    }

    @Test
    public void blankMatter_isNotInBook_notWildcard() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(4L, "NR04", null, 5, null, null, "999999.99",
                        "2020-01-01 00:00:00", null, 0, null));
        assertEquals(DeviceRuleVerdict.Outcome.NOT_IN_BOOK,
                svc.judge("  ", bd("1"), d("2026-01-01 00:00:00")).getOutcome());
    }

    @Test
    public void unresolved_whenAllRowsWithdrawnOrOutsideWindow() {
        TNwfmDeviceRule withdrawn = row(2L, "NR02", "衡器示值偏差", 3, "0.5", "2", "5",
                "2020-01-01 00:00:00", null, 1, null);
        TNwfmDeviceRule future = row(3L, "NR03", "衡器示值偏差", 4, "0.5", "2", "-1",
                "2027-01-01 00:00:00", null, 0, null);
        TNwfmDeviceRule past = row(4L, "NR04", "衡器示值偏差", 5, "0.5", "2", "5",
                null, "2020-01-01 00:00:00", 0, null);
        TNwfmDeviceRuleServiceImpl svc = serviceWith(withdrawn, future, past);

        DeviceRuleVerdict v = svc.judge("衡器示值偏差", bd("1"), d("2026-06-01 00:00:00"));
        assertEquals(DeviceRuleVerdict.Outcome.UNRESOLVED, v.getOutcome());
        // 备查行带回来：认这件事的行都在，只是此刻没一行在册管用
        assertEquals(3, v.getCandidates().size());
        assertNull(v.getLevel());
    }

    // ---------------- 口径六：空天花板与走人工 ----------------

    @Test
    public void emptyCeilingSwallowsEverything() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(0L, "NR00", "衡器示值偏差", 1, "0.50", "2.00", null,
                        null, null, 0, null));
        assertEquals(1, svc.judge("衡器示值偏差", bd("0.50"), now()).getLevel());
        assertEquals(2, svc.judge("衡器示值偏差", bd("2.00"), now()).getLevel());
        // 停用档无天花板：差得再多也算在停用档，不是人工
        assertEquals(3, svc.judge("衡器示值偏差", bd("2.01"), now()).getLevel());
        assertEquals(3, svc.judge("衡器示值偏差", bd("1000000"), now()).getLevel());
    }

    @Test
    public void negativeAndAbsurdCeilingsReadAsEmpty() {
        // 负数按空
        TNwfmDeviceRuleServiceImpl neg = serviceWith(
                row(3L, "NR03", "衡器示值偏差", 4, "0.5", "2", "-1.00",
                        null, null, 0, null));
        assertEquals(3, neg.judge("衡器示值偏差", bd("9999"), now()).getLevel());

        // 说不上来的大数按空，不当成比谁都大，也不当零
        TNwfmDeviceRuleServiceImpl big = serviceWith(
                row(4L, "NR04", "另一名目", 5, null, null, "999999.99",
                        null, null, 0, null));
        // 前两档天花板也空：任意偏差落档 1（第一段就无天花板）
        assertEquals(1, big.judge("另一名目", bd("0.00"), now()).getLevel());
        assertEquals(1, big.judge("另一名目", bd("50"), now()).getLevel());
    }

    @Test
    public void beyondHighestSolidCeiling_goesManual_noLevel() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(1L, "NR01", "衡器示值偏差", 1, "0.50", "2.00", "5.00",
                        null, null, 0, null));
        // 压在天花板上归本段
        assertEquals(1, svc.judge("衡器示值偏差", bd("0.50"), now()).getLevel());
        assertEquals(2, svc.judge("衡器示值偏差", bd("2.00"), now()).getLevel());
        assertEquals(3, svc.judge("衡器示值偏差", bd("5.00"), now()).getLevel());
        // 越过最高一道非空天花板：走人工，系统不给段
        DeviceRuleVerdict over = svc.judge("衡器示值偏差", bd("5.01"), now());
        assertEquals(DeviceRuleVerdict.Outcome.MANUAL, over.getOutcome());
        assertNull(over.getLevel());
    }

    @Test
    public void illegalDeviation() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(0L, "NR00", "衡器示值偏差", 1, "0.5", "2", null, null, null, 0, null));
        assertEquals(DeviceRuleVerdict.Outcome.ILLEGAL_INPUT,
                svc.judge("衡器示值偏差", null, now()).getOutcome());
        assertEquals(DeviceRuleVerdict.Outcome.ILLEGAL_INPUT,
                svc.judge("衡器示值偏差", bd("-0.01"), now()).getOutcome());
    }

    // ---------------- 口径五：名目检索逐字全等 ----------------

    @Test
    public void searchByItemName_exactOnly_trimAndBlanks() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(0L, "NR00", "衡器示值偏差", 2, "0.5", "2", null, null, null, 0, null),
                row(1L, "NR01", "衡器示值偏差", 1, "0.5", "2", "5", null, null, 0, null),
                row(2L, "NR02", "衡器示值偏差", 3, "0.5", "2", "5", null, null, 1, null),
                row(4L, "NR04", null, 5, null, null, "999999.99", null, null, 0, null),
                row(5L, "NR05", "台秤示值偏差", 6, "0.5", "2", "5", null, null, 0, null));

        // 一个名目认几件事就列几行（含撤下行，撤下也是认这件事的行）
        List<TNwfmDeviceRule> hit = svc.searchByItemName("衡器示值偏差");
        assertEquals(3, hit.size());
        // 按行次从上往下
        assertEquals(1, hit.get(0).getRowSeq().intValue());
        assertEquals(3, hit.get(2).getRowSeq().intValue());

        // 前后空白不算词的一部分（Java trim 只剥 ASCII 空白）
        assertEquals(3, svc.searchByItemName("   衡器示值偏差  ").size());

        // 只对上半截不列
        assertTrue(svc.searchByItemName("衡器").isEmpty());
        // 近义不同字不并条
        assertEquals(1, svc.searchByItemName("台秤示值偏差").size());
        // 空词 / null 一条不列
        assertTrue(svc.searchByItemName("  ").isEmpty());
        assertTrue(svc.searchByItemName(null).isEmpty());
        // 名目空着的行，什么词都检不出来
        assertTrue(svc.searchByItemName("").isEmpty());
    }

    // ---------------- 口径一：录行、行次、重复退回 ----------------

    @Test
    public void enroll_assignsSeq_rejectsDuplicateActiveCode() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(1L, "NR01", "衡器示值偏差", 1, "0.5", "2", "5", null, null, 0, null),
                row(2L, "NR02", "衡器示值偏差", 3, "0.5", "2", "5", null, null, 1, null));

        // 行次不许手填：塞 99 也被系统排
        TNwfmDeviceRule fresh = row(null, "NR10", "新名目", 99, "0.5", "2", null, null, null, 0, null);
        DeviceRuleEnrollReceipt ok = svc.enroll(fresh);
        assertTrue(ok.isAccepted());
        // 占着的行次 3 不重发：max(1,3)+1 = 4
        assertEquals(4, fresh.getRowSeq().intValue());

        // 同代号在册行已在，第二行退回
        DeviceRuleEnrollReceipt dup = svc.enroll(
                row(null, "NR01", "别的名目", null, "0.5", "2", null, null, null, 0, null));
        assertEquals(DeviceRuleEnrollReceipt.Status.DUPLICATE_ACTIVE, dup.getStatus());
        assertFalse(dup.isAccepted());
    }

    @Test
    public void enroll_overlappingWindowSameMatterRejected_adjacentAccepted() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(1L, "NR01", "衡器示值偏差", 1, "0.5", "2", "5",
                        "2020-01-01 00:00:00", "2021-01-01 00:00:00", 0, null));

        // 时段相交：退回
        DeviceRuleEnrollReceipt overlap = svc.enroll(row(null, "NR20", "衡器示值偏差", null,
                "0.5", "2", null, "2020-06-01 00:00:00", null, 0, null));
        assertEquals(DeviceRuleEnrollReceipt.Status.DUPLICATE_MATTER, overlap.getStatus());

        // 端点相接（新行 effStart == 旧行 effEnd）是半开区间，不相交，收下
        DeviceRuleEnrollReceipt adjacent = svc.enroll(row(null, "NR21", "衡器示值偏差", null,
                "0.5", "2", null, "2021-01-01 00:00:00", null, 0, null));
        assertTrue(adjacent.isAccepted());
    }

    @Test
    public void withdrawnCode_comesBackWithOriginalSeq() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith(
                row(2L, "NR02", "旧名目", 3, "0.5", "2", "5",
                        "2020-01-01 00:00:00", null, 1, null));

        TNwfmDeviceRule back = row(null, "NR02", "新名目", null,
                "0.5", "2", null, null, null, 0, null);
        DeviceRuleEnrollReceipt receipt = svc.enroll(back);
        assertEquals(DeviceRuleEnrollReceipt.Status.RESTORED, receipt.getStatus());
        // 认的还是当初那一个行次
        assertEquals(3, receipt.getRowSeq().intValue());
        TNwfmDeviceRule stored = svc.selectTNwfmDeviceRuleById(2L);
        assertEquals(0, stored.getStatus().intValue());
        assertEquals(3, stored.getRowSeq().intValue());
        assertEquals("新名目", stored.getItemName());
    }

    @Test
    public void enroll_illegalRowsAndSanitizedLimits() {
        TNwfmDeviceRuleServiceImpl svc = serviceWith();

        assertEquals(DeviceRuleEnrollReceipt.Status.ILLEGAL_ROW,
                svc.enroll(row(null, "  ", "名目", null, "0.5", "2", null, null, null, 0, null))
                        .getStatus());
        assertEquals(DeviceRuleEnrollReceipt.Status.ILLEGAL_ROW,
                svc.enroll(row(null, "NR1", "  ", null, "0.5", "2", null, null, null, 0, null))
                        .getStatus());

        // 负数/哨兵大数录进来即洗净为空
        TNwfmDeviceRule dirty = row(null, "NR1", "名目", null,
                "-3", "999999.99", "5", null, null, 0, null);
        DeviceRuleEnrollReceipt receipt = svc.enroll(dirty);
        assertTrue(receipt.isAccepted());
        assertNull(dirty.getTh1Max());
        assertNull(dirty.getTh2Max());
        assertEquals(new BigDecimal("5"), dirty.getTh3Max());
    }

    @Test
    public void withdraw_keepsRowAndSeq() {
        TNwfmDeviceRule r = row(7L, "NR07", "名目", 7, "0.5", "2", null, null, null, 0, null);
        TNwfmDeviceRuleServiceImpl svc = serviceWith(r);

        assertTrue(svc.withdraw(7L));
        assertEquals(1, svc.selectTNwfmDeviceRuleById(7L).getStatus().intValue());
        // 行没删，行次还在
        assertEquals(7, svc.listBook().get(0).getRowSeq().intValue());
        // 重复撤下幂等；查无此行 false
        assertTrue(svc.withdraw(7L));
        assertFalse(svc.withdraw(404L));
    }

    // ---------------- 夹具 ----------------

    private Date now() {
        return d("2026-10-02 10:00:00");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /** 造一行；null 字段就留空 */
    private static TNwfmDeviceRule row(Long id, String code, String item, Integer seq,
                                       String th1, String th2, String th3,
                                       String start, String end, Integer status, Date updateTime) {
        TNwfmDeviceRule r = new TNwfmDeviceRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(code);
        r.setItemName(item);
        r.setRowSeq(seq);
        if (th1 != null) {
            r.setTh1Max(new BigDecimal(th1));
        }
        if (th2 != null) {
            r.setTh2Max(new BigDecimal(th2));
        }
        if (th3 != null) {
            r.setTh3Max(new BigDecimal(th3));
        }
        r.setEffStart(start == null ? null : d(start));
        r.setEffEnd(end == null ? null : d(end));
        r.setStatus(status);
        r.setDelFlag(0);
        r.setUpdateTime(updateTime);
        r.setCreateTime(d("2026-01-01 00:00:00"));
        return r;
    }

    /** 内存册子冒充 mapper */
    @SuppressWarnings("unchecked")
    private TNwfmDeviceRuleServiceImpl serviceWith(TNwfmDeviceRule... rows) {
        final List<TNwfmDeviceRule> store = new ArrayList<>();
        java.util.Collections.addAll(store, rows);

        TNwfmDeviceRuleMapper mapper = mock(TNwfmDeviceRuleMapper.class);
        when(mapper.selectList(any())).thenAnswer(inv -> {
            // 只模拟实现里用到的等值条件：del_flag、item_name
            QueryWrapper<TNwfmDeviceRule> w = inv.getArgument(0);
            Collection<Object> vals = w.getParamNameValuePairs().values();
            boolean hasNameCond = w.getSqlSegment() != null && w.getSqlSegment().contains("item_name");
            Object[] params = vals.toArray();
            // 参数位置不依赖 MP 内部编号：按类型认，Integer 是 del_flag，String 是名目
            Object delVal = null;
            Object nameVal = null;
            for (Object v : params) {
                if (v instanceof Integer) {
                    delVal = v;
                } else if (v instanceof String) {
                    nameVal = v;
                }
            }
            List<TNwfmDeviceRule> out = new ArrayList<>();
            for (TNwfmDeviceRule r : store) {
                if (r.getDelFlag() != null && !r.getDelFlag().equals(delVal)) {
                    continue;
                }
                if (hasNameCond
                        && (r.getItemName() == null || !r.getItemName().equals(nameVal))) {
                    continue;
                }
                out.add(r);
            }
            return out;
        });
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TNwfmDeviceRule r : store) {
                if (r.getId() != null && r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
        when(mapper.insert(any())).thenAnswer(inv -> {
            TNwfmDeviceRule r = inv.getArgument(0);
            if (r.getId() == null) {
                long next = store.size() + 1000L;
                r.setId(next);
            }
            store.add(r);
            return 1;
        });
        when(mapper.updateById(any())).thenAnswer(inv -> {
            TNwfmDeviceRule r = inv.getArgument(0);
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId() != null && store.get(i).getId().equals(r.getId())) {
                    store.set(i, r);
                    return 1;
                }
            }
            return 0;
        });

        TNwfmDeviceRuleServiceImpl svc = new TNwfmDeviceRuleServiceImpl();
        java.lang.reflect.Field f;
        try {
            f = TNwfmDeviceRuleServiceImpl.class.getDeclaredField("nwfmDeviceRuleMapper");
            f.setAccessible(true);
            f.set(svc, mapper);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return svc;
    }
}
