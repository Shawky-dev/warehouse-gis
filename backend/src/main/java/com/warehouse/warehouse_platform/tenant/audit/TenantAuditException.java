package com.warehouse.warehouse_platform.tenant.audit;

import org.springframework.http.HttpStatus;

public class TenantAuditException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TenantAuditException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TenantAuditException badRequest(String message) {
        return new TenantAuditException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
