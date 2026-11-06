package org.cardanofoundation.tools.adahandle.resolver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ada_handle")
public class AdaHandle {
    @Id
    @Column(unique = true)
    private String name;
    /**
     * Null when the address the handle resolves to has no stake part (an enterprise or pointer address), or, for a
     * virtual sub-handle, when its datum's address is for another network (see {@code VirtualSubHandleDatum}).
     */
    @Column(name = "stake_address")
    private @Nullable String stakeAddress;
    @Column(name = "payment_address")
    private String paymentAddress;
}
