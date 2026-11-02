package org.cardanofoundation.tools.adahandle.resolver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
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
    /** The handle's token was burned at this slot: from here on the handle doesn't exist (no addresses). */
    @Column(name = "burned")
    private boolean burned;

    public AdaHandleHistoryItem(String name, String stakeAddress, String paymentAddress, long slot) {
        this.name = name;
        this.stakeAddress = stakeAddress;
        this.paymentAddress = paymentAddress;
        this.slot = slot;
    }

    public static AdaHandleHistoryItem burn(String name, long slot) {
        AdaHandleHistoryItem item = new AdaHandleHistoryItem(name, null, null, slot);
        item.burned = true;
        return item;
    }
}
