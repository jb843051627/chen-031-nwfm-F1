-- 口径册种子（t_nwfm_device_rule）。同名目 = 同一件事；一件事只容一行说话，
-- 多版本按时段（eff_end 不含）+ 更新时刻晚者 + 代号字面次序裁决；撤下不删行。
-- NR00/NR01：同名目新旧两版，2021-01-01 00:00:00 整切换（旧版管到这一刻、不含）；
-- NR02：时段重叠但已撤下，在册裁决不吭声，行次仍占着；
-- NR03：未来版本，且停用档上限写负数 ⇒ 按空（无天花板）；
-- NR04：名目空着 ⇒ 名目检索一条不列；停用档写哨兵大数 ⇒ 按空；
-- NR05：名目与 NR00 近义不同字，逐字比不得并入。
INSERT IGNORE INTO t_nwfm_device_rule (id, rule_code, rule_name, item_name, row_seq, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time)
VALUES (0, 'NR00', '偏差处置档（现行版：准秤0.5/复称2.0/停用无天花板）', '衡器示值偏差', 2, 0.50, 2.00, NULL, '2021-01-01 00:00:00', '2099-12-31 23:59:59', 0, 0, 0, 'seed', NOW()),
       (1, 'NR01', '偏差处置档（旧版：准秤0.5/复称2.0/停用5.0）', '衡器示值偏差', 1, 0.50, 2.00, 5.00, '2020-01-01 00:00:00', '2021-01-01 00:00:00', 0, 0, 0, 'seed', NOW()),
       (2, 'NR02', '偏差停用档旧口径（已撤下，不删行仍占行次）', '衡器示值偏差', 3, 0.50, 2.00, 5.00, '2020-01-01 00:00:00', '2099-12-31 23:59:59', 0, 1, 0, 'seed', NOW()),
       (3, 'NR03', '偏差处置档（未来版；停用档负数按空）', '衡器示值偏差', 4, 0.50, 2.00, -1.00, '2027-01-01 00:00:00', '2099-12-31 23:59:59', 0, 0, 0, 'seed', NOW()),
       (4, 'NR04', '无名目行（名目检索不出；哨兵大数按空）', NULL, 5, NULL, NULL, 999999.99, '2020-01-01 00:00:00', '2099-12-31 23:59:59', 0, 0, 0, 'seed', NOW()),
       (5, 'NR05', '近名名目行（逐字比，不得与衡器示值偏差并作一条）', '台秤示值偏差', 6, 0.50, 2.00, 5.00, '2020-01-01 00:00:00', '2099-12-31 23:59:59', 0, 0, 0, 'seed', NOW());

INSERT IGNORE INTO t_nwfm_settle_rule (id, bill_no, node_no, sign_mode, need_count, sign_count, status, del_flag, create_by, create_time)
VALUES (0, 'NB00', 0, 0, 1, 0, 0, 0, 'seed', NOW()),
       (1, 'NB01', 1, 1, 2, 0, 0, 0, 'seed', NOW());

INSERT IGNORE INTO t_nwfm_dispute_rule (id, biz_no, stage, status, content, last_action, del_flag, create_by, create_time)
VALUES (0, 'ND00', 0, 0, '当场处置档的基准单：争议金额落在 50 元以内', 'seed', 0, 'seed', NOW()),
       (1, 'ND01', 1, 0, '复称处置档的基准单：争议金额 50 元至 200 元', 'seed', 0, 'seed', NOW());

INSERT IGNORE INTO t_nwfm_settle_base (id, grade_code, grade_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'GB00', '摊分直赔档：赔付额在 100 元以内（在用行）', 100.00, 500.00, 2000.00, 0, 0, 'seed', NOW()),
       (1, 'GB01', '摊分直赔档：赔付额在 100 元以内（在用行）', 100.00, 500.00, 2000.00, 0, 0, 'seed', NOW()),
       (2, 'GB02', '摊分复核档：赔付额 100 元至 500 元（停用行）', 100.00, 500.00, 2000.00, 1, 0, 'seed', NOW());
