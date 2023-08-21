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
public class AdaHandleHistoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String name;
    @Column(name = "stake_address")
    private String stakeAddress;
    @Column(name = "payment_address")
    private String paymentAddress;
    @Column(name = "slot")
    private long slot;
}
