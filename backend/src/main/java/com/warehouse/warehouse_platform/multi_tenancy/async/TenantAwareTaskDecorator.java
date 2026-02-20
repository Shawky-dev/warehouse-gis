package com.warehouse.warehouse_platform.multi_tenancy.async;

import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;

/*
    * TaskDecorator that copies tenant context from the parent thread to the child thread when executing async tasks.
    * This ensures that any code running in async tasks can access the correct tenant context.
*/
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        String tenantId = TenantContext.getTenantId();
        return () -> {
            try {
                TenantContext.setTenantId(tenantId);
                runnable.run();
            } finally {
                /*
                    clears the tenant contex in the Task thread 
                */
                TenantContext.setTenantId(null);
            }
        };
    }
}