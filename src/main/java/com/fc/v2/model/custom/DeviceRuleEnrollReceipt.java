package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TNwfmDeviceRule;

/**
 * 口径册录行回执。
 *
 * <ul>
 *   <li>{@link Status#ACCEPTED} 收下：新行给新行次；撤下后补回的沿用原行次；</li>
 *   <li>{@link Status#DUPLICATE_ACTIVE} 同代号在册行已在：第二行退回，不按新行算数；</li>
 *   <li>{@link Status#DUPLICATE_MATTER} 同一名目、同一时段已有在册行（一件事只容一行），退回；</li>
 *   <li>{@link Status#ILLEGAL_ROW} 行本身不合法（代号空、名目空等），退回。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-02
 */
public class DeviceRuleEnrollReceipt {

    public enum Status {
        /** 收下（新行） */
        ACCEPTED,
        /** 收下（撤下行补回，沿用原行次） */
        RESTORED,
        /** 同代号在册行已在，退回 */
        DUPLICATE_ACTIVE,
        /** 同名目同时段在册行已在，退回 */
        DUPLICATE_MATTER,
        /** 行不合法，退回 */
        ILLEGAL_ROW
    }

    private final Status status;
    private final Integer rowSeq;
    private final TNwfmDeviceRule row;
    private final String message;

    private DeviceRuleEnrollReceipt(Status status, Integer rowSeq, TNwfmDeviceRule row, String message) {
        this.status = status;
        this.rowSeq = rowSeq;
        this.row = row;
        this.message = message;
    }

    public static DeviceRuleEnrollReceipt accepted(TNwfmDeviceRule row, String message) {
        return new DeviceRuleEnrollReceipt(Status.ACCEPTED, row.getRowSeq(), row, message);
    }

    public static DeviceRuleEnrollReceipt restored(TNwfmDeviceRule row, String message) {
        return new DeviceRuleEnrollReceipt(Status.RESTORED, row.getRowSeq(), row, message);
    }

    public static DeviceRuleEnrollReceipt reject(Status status, String message) {
        return new DeviceRuleEnrollReceipt(status, null, null, message);
    }

    public Status getStatus() {
        return status;
    }

    public Integer getRowSeq() {
        return rowSeq;
    }

    public TNwfmDeviceRule getRow() {
        return row;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED || status == Status.RESTORED;
    }
}
