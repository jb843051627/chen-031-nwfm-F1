package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 计量器具判定规则对象 t_nwfm_device_rule
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_nwfm_device_rule")
@ApiModel(value = "TNwfmDeviceRule", description = "计量器具判定规则")
public class TNwfmDeviceRule implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 规则编号 */
    @TableField("rule_code")
    @ApiModelProperty(value = "规则编号")
    private String ruleCode;

    /** 规则名称 */
    @TableField("rule_name")
    @ApiModelProperty(value = "规则名称")
    private String ruleName;

    /** 名目：本行认的是哪一件事；空者不进名目检索，也不挂任何一件事 */
    @TableField("item_name")
    @ApiModelProperty(value = "名目（本行认的是哪一件事；空者不进名目检索）")
    private String itemName;

    /** 行次：系统在上头排定，不许手填；一行退回后再补，认的还是原行次 */
    @TableField("row_seq")
    @ApiModelProperty(value = "行次（系统排定，不许手填；退回再补仍认原行次）")
    private Integer rowSeq;

    /** 准秤档上限(%)；空/负/哨兵大数 = 此档无天花板 */
    @TableField("th1_max")
    @ApiModelProperty(value = "准秤档上限(%)；空/负/哨兵大数=此档无天花板")
    private BigDecimal th1Max;

    /** 复称档上限(%)；空/负/哨兵大数 = 此档无天花板 */
    @TableField("th2_max")
    @ApiModelProperty(value = "复称档上限(%)；空/负/哨兵大数=此档无天花板")
    private BigDecimal th2Max;

    /** 停用档上限(%)；空/负/哨兵大数 = 此档无天花板，再超出走人工 */
    @TableField("th3_max")
    @ApiModelProperty(value = "停用档上限(%)；空/负/哨兵大数=此档无天花板，再超出走人工")
    private BigDecimal th3Max;

    /** 生效起始时刻（含；空着 = 从头管起） */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "生效起始时刻(含；空着=从头管起)")
    private Date effStart;

    /** 生效截止时刻（不含：到头那一刻自己不管用；空着 = 管到永远） */
    @TableField("eff_end")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "生效截止时刻(不含，空着=管到永远)")
    private Date effEnd;

    /** 旧优先级列：口径裁决不看它（同名目多版本比更新时刻、再比代号字面次序） */
    @TableField("priority")
    @ApiModelProperty(value = "旧优先级列（裁决不看）")
    private Integer priority;

    /** 在册状态 0在册 1撤下（撤下不删行，仍占原行次） */
    @TableField("status")
    @ApiModelProperty(value = "在册状态 0在册 1撤下")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getRowSeq() {
        return rowSeq;
    }

    public void setRowSeq(Integer rowSeq) {
        this.rowSeq = rowSeq;
    }

    public BigDecimal getTh1Max() {
        return th1Max;
    }

    public void setTh1Max(BigDecimal th1Max) {
        this.th1Max = th1Max;
    }

    public BigDecimal getTh2Max() {
        return th2Max;
    }

    public void setTh2Max(BigDecimal th2Max) {
        this.th2Max = th2Max;
    }

    public BigDecimal getTh3Max() {
        return th3Max;
    }

    public void setTh3Max(BigDecimal th3Max) {
        this.th3Max = th3Max;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
