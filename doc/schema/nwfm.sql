-- nwfm 农贸市场计量器具公平交易智慧监管 -- schema (chen-031)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_031

CREATE TABLE IF NOT EXISTS t_nwfm_check_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '册次码',
  row_no int DEFAULT NULL COMMENT '原始行次',
  item_code varchar(64) DEFAULT NULL COMMENT '条目代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本次核收器具数',
  device_kind varchar(32) DEFAULT NULL COMMENT '器具种类',
  stall_name varchar(64) DEFAULT NULL COMMENT '摊户名称',
  status int DEFAULT NULL COMMENT '条目状态 0待核 1已核 2已处理',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='周期核收条目';

CREATE TABLE IF NOT EXISTS t_nwfm_device_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(32) DEFAULT NULL COMMENT '规则代号',
  rule_name varchar(64) DEFAULT NULL COMMENT '规则名称',
  item_name varchar(64) DEFAULT NULL COMMENT '名目（本行认的是哪一件事；空者不进名目检索，也不挂任何一件事）',
  row_seq int DEFAULT NULL COMMENT '行次（系统在上头排定，不许手填；一行退回后再补，认的还是原行次）',
  th1_max decimal(8,2) DEFAULT NULL COMMENT '准秤档上限(%)；空/负/哨兵大数=此档无天花板',
  th2_max decimal(8,2) DEFAULT NULL COMMENT '复称档上限(%)；空/负/哨兵大数=此档无天花板',
  th3_max decimal(8,2) DEFAULT NULL COMMENT '停用档上限(%)；空/负/哨兵大数=此档无天花板，超最高档非空上限走人工',
  eff_start datetime DEFAULT NULL COMMENT '生效起始时刻(含；空着=从头管起)',
  eff_end datetime DEFAULT NULL COMMENT '生效截止时刻(不含，到头那一刻自己不管用；空着=管到永远)',
  priority int DEFAULT NULL COMMENT '旧优先级列：口径裁决不看它，同名目多版本先比更新时刻晚者、再比代号字面次序',
  status int DEFAULT NULL COMMENT '在册状态 0在册 1撤下（撤下不删行，仍占原行次）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计量器具判定规则';

CREATE TABLE IF NOT EXISTS t_nwfm_dispute_rule (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '争议代号',
  stage int DEFAULT NULL COMMENT '当前环节 0..3',
  status int DEFAULT NULL COMMENT '争议状态 0待发起 1在办 2已办结',
  content varchar(255) DEFAULT NULL COMMENT '内容备注',
  last_action varchar(32) DEFAULT NULL COMMENT '最近一次流转动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计量争议调解判定规则';

CREATE TABLE IF NOT EXISTS t_nwfm_remind_row (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '条目编号',
  due_at datetime DEFAULT NULL COMMENT '到期时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '提醒提前天数',
  notify_type varchar(32) DEFAULT NULL COMMENT '提醒方式',
  stall_name varchar(64) DEFAULT NULL COMMENT '摊户名称',
  status int DEFAULT NULL COMMENT '条目状态 0待发 1已发 2已处理',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='到期提醒条目';

CREATE TABLE IF NOT EXISTS t_nwfm_settle_base (
  id bigint NOT NULL COMMENT '主键',
  grade_code varchar(32) DEFAULT NULL COMMENT '分档编号',
  grade_name varchar(64) DEFAULT NULL COMMENT '分档名称',
  th1_max decimal(8,2) DEFAULT NULL COMMENT '摊分档上限(元)',
  th2_max decimal(8,2) DEFAULT NULL COMMENT '摊分复核档上限(元)',
  th3_max decimal(8,2) DEFAULT NULL COMMENT '摊分调解档上限(元)',
  status int DEFAULT NULL COMMENT '分档状态 0启用 1撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赔付摊分档档案';

CREATE TABLE IF NOT EXISTS t_nwfm_settle_row (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '条目编号',
  base_id bigint DEFAULT NULL COMMENT '分档档案ID',
  grade_code varchar(32) DEFAULT NULL COMMENT '分档编号(冗余，以档案为准)',
  amount decimal(12,2) DEFAULT NULL COMMENT '赔付金额(元)',
  settle_type varchar(32) DEFAULT NULL COMMENT '清算方式',
  stall_name varchar(64) DEFAULT NULL COMMENT '摊户名称',
  status int DEFAULT NULL COMMENT '条目状态 0未清算 1已清算 2已回退',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清算赔付条目';

CREATE TABLE IF NOT EXISTS t_nwfm_settle_rule (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '赔付代号',
  node_no int DEFAULT NULL COMMENT '当前关口 0..2',
  sign_mode int DEFAULT NULL COMMENT '落签模式 0或签 1齐签',
  need_count int DEFAULT NULL COMMENT '本关口应落人数',
  sign_count int DEFAULT NULL COMMENT '本关口已落人数',
  status int DEFAULT NULL COMMENT '赔付状态 0在办 1已定下 2已封住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清算赔付判定规则';

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

