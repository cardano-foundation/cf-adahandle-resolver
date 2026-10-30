package org.cardanofoundation.tools.adahandle.resolver.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// JPA requires equals/hashCode on a composite key class (Hibernate warns HHH000039 otherwise).
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AdaHandleHistoryItemKey implements Serializable {
    private String name;
    private long slot;
}
