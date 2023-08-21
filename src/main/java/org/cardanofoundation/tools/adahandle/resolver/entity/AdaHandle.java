package org.cardanofoundation.tools.adahandle.resolver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Column(name = "stake_address")
    private String stakeAddress;
    @Column(name = "payment_address")
    private String paymentAddress;
}
