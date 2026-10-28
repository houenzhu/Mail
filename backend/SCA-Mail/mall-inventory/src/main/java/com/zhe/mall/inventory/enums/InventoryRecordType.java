package com.zhe.mall.inventory.enums;

public enum InventoryRecordType {

    DEDUCT(1, "扣减"),

    ROLLBACK(2, "回滚"),

    PRE_DEDUCT(3, "预扣"),

    PRE_ROLLBACK(4, "预扣回滚");

    private final int code;

    private final String label;

    InventoryRecordType(int code, String label) {
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
