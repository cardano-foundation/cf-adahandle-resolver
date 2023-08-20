package org.cardanofoundation.tools.adahandle.resolver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdaHandle {
    @Column(unique = true)
    private String name;
    @Column(name = "stake_address")
    private String stakeAddress;
}
