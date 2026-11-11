package org.cardanofoundation.tools.adahandle.resolver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ada_handle_history_item")
@IdClass(AdaHandleHistoryItemKey.class) // Use IdClass to specify the composite key class
public class AdaHandleHistoryItem {
    @Id
    private String name;
    /** Null for a burn, and when the address has no stake part (see {@link AdaHandle}). */
    @Column(name = "stake_address")
    private @Nullable String stakeAddress;
    /** Null only for a burn. */
    @Column(name = "payment_address")
    private @Nullable String paymentAddress;
    @Id
    private long slot;
    /** The handle's token was burned at this slot: from here on the handle doesn't exist (no addresses). */
    @Column(name = "burned")
    private boolean burned;
    /**
     * The token (policy id + asset name, hex) whose output or burn this change comes from. Not stored: it is only used
     * to order the changes of one block, see {@code AdaHandleProcessor}. Null on entries read from the database.
     */
    @Transient
    private @Nullable String token;

    public AdaHandleHistoryItem(String name, @Nullable String stakeAddress, @Nullable String paymentAddress, long slot) {
        this.name = name;
        this.stakeAddress = stakeAddress;
        this.paymentAddress = paymentAddress;
        this.slot = slot;
    }

    public static AdaHandleHistoryItem burn(String name, long slot, String token) {
        AdaHandleHistoryItem item = new AdaHandleHistoryItem(name, null, null, slot);
        item.burned = true;
        item.token = token;
        return item;
    }
}
