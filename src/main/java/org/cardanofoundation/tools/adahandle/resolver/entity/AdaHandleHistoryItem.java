package org.cardanofoundation.tools.adahandle.resolver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ada_handle_history_item")
@IdClass(AdaHandleHistoryItemKey.class) // Use IdClass to specify the composite key class
public class AdaHandleHistoryItem {
    @Id
    private String name;
    @Column(name = "stake_address")
    private String stakeAddress;
    @Column(name = "payment_address")
    private String paymentAddress;
    @Id
    private long slot;
}
