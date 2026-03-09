package com.warehouse.warehouse_platform.tenant.product;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductSupplierId implements Serializable {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
}
