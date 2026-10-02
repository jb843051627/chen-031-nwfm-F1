package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNwfmDeviceRuleMapper;
import com.fc.v2.model.auto.TNwfmDeviceRule;
import com.fc.v2.model.custom.NwfmRuleEvalResult;

/**
 * 口径册（计量器具判定规则）逐条口径的单元测试；mapper 全部打桩，不连库。
 *
 * @author fuce
 * @date 2026-10-02
 */
class TNwfmDeviceRuleServiceImplTest {

    private TNwfmDeviceRuleMapper mapper;
    private TNwfmDeviceRuleServiceImpl service;

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static final Date AT = d("2026-06-15 12:00:00");

    private static TNwfmDeviceRule rule(long id, String code, String th1, String th2, String th3,
                                        int status, Date updateTime) {
        TNwfmDeviceRule r = new TNwfmDeviceRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(code + "号口径");
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setStatus(status);
        r.setDelFlag(0);
        r.setEffStart(d("2020-01-01 00:00:00"));
        r.setEffEnd(d("2099-12-31 23:59:59"));
        r.setCreateTime(d("2020-01-01 00:00:00"));
        r.setUpdateTime(updateTime);
        return r;
    }

    @BeforeEach
    void setUp() {
        this.mapper = mock(TNwfmDeviceRuleMapper.class);
        this.service = new TNwfmDeviceRuleServiceImpl();
        ReflectionTestUtils.setField(this.service, "nwfmDeviceRuleMapper", this.mapper);
    }

    // ---------- 口径二：改得晚的说话 ----------

    @Test
    void 认行_改得晚的那行说话() {
        TNwfmDeviceRule older = rule(1L, "NR01", "0.50", "2.00", "5.00", 0, d("2026-01-01 00:00:00"));
        TNwfmDeviceRule newer = rule(2L, "NR01", "0.10", "2.00", "5.00", 0, d("2026-06-01 00:00:00"));
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(older, newer)));

        NwfmRuleEvalResult r = service.evaluateDetail("NR01", new BigDecimal("0.30"), AT);

        assertEquals(2, r.getLevel());          // newer 的 th1=0.10，0.30 落复核档
        assertEquals(Long.valueOf(2L), r.getRuleId());
        assertNull(r.getReason());
    }

    @Test
    void 认行_改得一样晚看代号字面次序() {
        // 字面次序 'NR10' < 'NR9'（'1'<'9'）；若按数字大小则 NR9 在前——必须按字面
        TNwfmDeviceRule nr9 = rule(1L, "NR9", "0.50", "2.00", "5.00", 0, d("2026-06-01 00:00:00"));
        TNwfmDeviceRule nr10 = rule(2L, "NR10", "0.10", "2.00", "5.00", 0, d("2026-06-01 00:00:00"));
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(nr9, nr10)));

        NwfmRuleEvalResult r = service.evaluateTopDetail(new BigDecimal("0.30"), AT);

        assertEquals("NR10", r.getRuleCode());
        assertEquals(2, r.getLevel());
    }

    // ---------- 口径三：到头那一刻不含 ----------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void 窗口_起始含截止不含_走墙钟字符串() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<>());
        service.listAvailable(AT);

        ArgumentCaptor<QueryWrapper> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectList(cap.capture());
        String seg = cap.getValue().getSqlSegment();
        assertTrue(seg.contains("eff_start <="), "起始时刻要含: " + seg);
        assertTrue(seg.contains("eff_end >"), "截止时刻要不含(严格大于): " + seg);
    }

    // ---------- 口径四：退回写明原因，不挑段充数 ----------

    @Test
    void 退回_册上没这一段_段不在册() {
        TNwfmDeviceRule other = rule(1L, "NR01", "0.50", "2.00", "5.00", 0, d("2026-01-01 00:00:00"));
        when(mapper.selectList(any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(other)))   // listAvailable
                .thenReturn(new ArrayList<>());                                  // existsAnyRowOfCode

        NwfmRuleEvalResult r = service.evaluateDetail("NR99", new BigDecimal("0.30"), AT);

        assertEquals(0, r.getLevel());
        assertEquals(NwfmRuleEvalResult.REASON_NOT_IN_REGISTRY, r.getReason());
        assertNull(r.getRuleId());
        assertNull(r.getRuleCode());
    }

    @Test
    void 退回_认它的行全被撤下_认不出() {
        TNwfmDeviceRule withdrawn = rule(1L, "NR01", "0.50", "2.00", "5.00", 1, d("2026-01-01 00:00:00"));
        when(mapper.selectList(any()))
                .thenReturn(new ArrayList<>())                                      // listAvailable：在用行为空
                .thenReturn(new ArrayList<>(Collections.singletonList(withdrawn))); // 册上有这一号

        NwfmRuleEvalResult r = service.evaluateDetail("NR01", new BigDecimal("0.30"), AT);

        assertEquals(0, r.getLevel());
        assertEquals(NwfmRuleEvalResult.REASON_UNRECOGNIZABLE, r.getReason());
        assertNull(r.getRuleId());
    }

    @Test
    void 退回_全册_空册段不在册_有行认不出() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<>());
        when(mapper.selectCount(any())).thenReturn(0);
        NwfmRuleEvalResult empty = service.evaluateTopDetail(new BigDecimal("0.30"), AT);
        assertEquals(NwfmRuleEvalResult.REASON_NOT_IN_REGISTRY, empty.getReason());

        when(mapper.selectCount(any())).thenReturn(2);
        NwfmRuleEvalResult hasRows = service.evaluateTopDetail(new BigDecimal("0.30"), AT);
        assertEquals(NwfmRuleEvalResult.REASON_UNRECOGNIZABLE, hasRows.getReason());
    }

    @Test
    void 退回_参数无效() {
        assertEquals(0, service.evaluate(null, new BigDecimal("0.3"), AT));
        assertEquals(0, service.evaluate("NR01", null, AT));
        assertEquals(0, service.evaluate("NR01", new BigDecimal("0.3"), null));
        NwfmRuleEvalResult neg = service.evaluateTopDetail(new BigDecimal("-0.10"), AT);
        assertEquals(0, neg.getLevel());
        assertEquals(NwfmRuleEvalResult.REASON_INVALID, neg.getReason());
        verify(mapper, never()).selectList(any());
    }

    // ---------- 口径六：上限与人工 ----------

    private NwfmRuleEvalResult evalTop(String th1, String th2, String th3, String input) {
        TNwfmDeviceRule row = rule(1L, "NR01", th1, th2, th3, 0, d("2026-01-01 00:00:00"));
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Collections.singletonList(row)));
        return service.evaluateTopDetail(new BigDecimal(input), AT);
    }

    @Test
    void 落段_上限是上一段的天花板_含在本段() {
        assertEquals(1, evalTop("0.50", "2.00", "5.00", "0.50").getLevel());  // 等于上限仍算准
        assertEquals(2, evalTop("0.50", "2.00", "5.00", "0.51").getLevel());
        assertEquals(2, evalTop("0.50", "2.00", "5.00", "2.00").getLevel());
        assertEquals(3, evalTop("0.50", "2.00", "5.00", "5.00").getLevel());
    }

    @Test
    void 落段_上限空着没有天花板() {
        assertEquals(1, evalTop(null, "2.00", "5.00", "999").getLevel());   // th1 空：再多也算准
        assertEquals(2, evalTop("0.50", null, "5.00", "999").getLevel());   // th2 空：再多也复称
        assertEquals(3, evalTop("0.50", "2.00", null, "999").getLevel());   // th3 空：再多也停用
    }

    @Test
    void 落段_负数与说不上的大数一概按空着() {
        assertEquals(1, evalTop("-1.00", "2.00", "5.00", "999").getLevel());       // 负数不当成零
        assertEquals(1, evalTop("999999.99", "2.00", "5.00", "500000").getLevel()); // 大数不当成真天花板
    }

    @Test
    void 落段_差得过分走人工_系统不落段() {
        NwfmRuleEvalResult r = evalTop("0.50", "2.00", "5.00", "8.00");
        assertEquals(4, r.getLevel());
        assertTrue(r.isManual());
        assertFalse(r.isRejected());
        assertEquals(Long.valueOf(1L), r.getRuleId());  // 行认下了，只是这一段不落数
    }

    // ---------- 口径五：名目检索 ----------

    @Test
    void 检索_逐字全等_空白不算词_空名目不列() {
        TNwfmDeviceRule a = rule(1L, "NR01", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        a.setRuleName("青菜摊每日核秤");
        TNwfmDeviceRule b = rule(2L, "NR02", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        b.setRuleName("青菜摊每日核秤 ");   // 尾巴多个空格是另一个名目，不许并
        TNwfmDeviceRule c = rule(3L, "NR03", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        c.setRuleName("青菜摊");
        TNwfmDeviceRule e = rule(4L, "NR04", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        e.setRuleName(null);                // 名目空着检索不出
        TNwfmDeviceRule f = rule(5L, "NR05", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        f.setRuleName("青菜摊每日核秤");
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Arrays.asList(a, b, c, e, f)));

        List<TNwfmDeviceRule> hits = service.listByName("  青菜摊每日核秤　"); // 关键词首尾空白不算
        assertEquals(2, hits.size());
        assertEquals(Long.valueOf(1L), hits.get(0).getId());  // 按行次往下排
        assertEquals(Long.valueOf(5L), hits.get(1).getId());

        assertTrue(service.listByName("青菜摊每日").isEmpty());  // 只对上半截不列
        assertTrue(service.listByName("").isEmpty());
        assertTrue(service.listByName("   ").isEmpty());
        assertTrue(service.listByName(null).isEmpty());
    }

    // ---------- 口径一：登记、退回、补行认当初行次 ----------

    @Test
    void 登记_同一事已有在用行_第二行退回() {
        TNwfmDeviceRule live = rule(9L, "NR09", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Collections.singletonList(live)));

        TNwfmDeviceRule dup = rule(0L, "NR09", "1", "3", "6", 0, null);
        assertEquals(0, service.addRule(dup));
        verify(mapper, never()).insert(any());
        verify(mapper, never()).updateById(any());
    }

    @Test
    void 登记_退了的行再补_认当初那一个行次() {
        TNwfmDeviceRule withdrawn = rule(42L, "NR09", "0.5", "2", "5", 1, d("2026-01-01 00:00:00"));
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Collections.singletonList(withdrawn)));
        when(mapper.updateById(any())).thenReturn(1);

        TNwfmDeviceRule again = new TNwfmDeviceRule();
        again.setRuleCode("NR09");
        again.setRuleName("新口径");
        again.setTh1Max(new BigDecimal("1.00"));
        assertEquals(1, service.addRule(again));

        ArgumentCaptor<TNwfmDeviceRule> cap = ArgumentCaptor.forClass(TNwfmDeviceRule.class);
        verify(mapper).updateById(cap.capture());
        verify(mapper, never()).insert(any());
        TNwfmDeviceRule saved = cap.getValue();
        assertEquals(Long.valueOf(42L), saved.getId());   // 还是当初那一个行次
        assertEquals(Integer.valueOf(0), saved.getStatus());
        assertEquals(new BigDecimal("1.00"), saved.getTh1Max());
    }

    @Test
    void 登记_新行行次系统排_不许自己敲() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<>());
        when(mapper.insert(any())).thenReturn(1);

        TNwfmDeviceRule fresh = new TNwfmDeviceRule();
        fresh.setId(999L);                                  // 自己敲的行次，不理会
        fresh.setRuleCode("NR10");
        assertEquals(1, service.addRule(fresh));

        ArgumentCaptor<TNwfmDeviceRule> cap = ArgumentCaptor.forClass(TNwfmDeviceRule.class);
        verify(mapper).insert(cap.capture());
        assertNull(cap.getValue().getId());
        assertEquals(Integer.valueOf(0), cap.getValue().getStatus());
        assertEquals(Integer.valueOf(0), cap.getValue().getDelFlag());
    }

    @Test
    void 登记_没有代号不成行() {
        assertEquals(0, service.addRule(null));
        assertEquals(0, service.addRule(new TNwfmDeviceRule()));
        verify(mapper, never()).insert(any());
    }

    @Test
    void 撤下_行还留在册面上() {
        TNwfmDeviceRule live = rule(7L, "NR07", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        when(mapper.selectById(7L)).thenReturn(live);
        when(mapper.updateById(any())).thenReturn(1);

        assertEquals(1, service.withdrawRule(7L));
        ArgumentCaptor<TNwfmDeviceRule> cap = ArgumentCaptor.forClass(TNwfmDeviceRule.class);
        verify(mapper).updateById(cap.capture());
        assertEquals(Integer.valueOf(1), cap.getValue().getStatus());

        when(mapper.selectById(99L)).thenReturn(null);
        assertEquals(0, service.withdrawRule(99L));
        assertEquals(0, service.withdrawRule(null));
    }

    // ---------- 旁路口径一致 ----------

    @Test
    void usable与count走同一套口径() {
        when(mapper.selectCount(any())).thenReturn(1);
        assertTrue(service.usable(1L, AT));
        when(mapper.selectCount(any())).thenReturn(0);
        assertFalse(service.usable(1L, AT));
        assertFalse(service.usable(null, AT));
        assertFalse(service.usable(1L, null));

        TNwfmDeviceRule row = rule(1L, "NR01", "0.5", "2", "5", 0, d("2026-01-01 00:00:00"));
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(Collections.singletonList(row)));
        assertEquals(1, service.countAvailable(AT));
        assertEquals(0, service.countAvailable(null));
        assertTrue(service.listAvailable(null).isEmpty());
    }
}
