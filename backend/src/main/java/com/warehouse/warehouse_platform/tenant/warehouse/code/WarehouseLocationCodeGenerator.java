package com.warehouse.warehouse_platform.tenant.warehouse.code;

import org.springframework.stereotype.Component;

@Component
public class WarehouseLocationCodeGenerator {

    public String generate(String layoutCode, String aisleCode, String side, String bayCode, int levelNum, int shelfNum) {
        return layoutCode + "-" + aisleCode + "-" + side + "-" + bayCode + "-L" + levelNum + "-S" + shelfNum;
    }
}
