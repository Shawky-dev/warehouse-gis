package com.warehouse.warehouse_platform.tenant.ifc;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class IfcModelNotFoundException extends RuntimeException {

    public IfcModelNotFoundException(String message) {
        super(message);
    }
}
