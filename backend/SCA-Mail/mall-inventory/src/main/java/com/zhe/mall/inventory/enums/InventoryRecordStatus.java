package com.zhe.mall.inventory.enums;

public enum InventoryRecordStatus {

    PROCESSING(0, "处理中"),

    SUCCESS(1, "成功"),

    FAILED(2, "失败");

    private final int code;

    private final String label;

    InventoryRecordStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }
}
