package org.cardanofoundation.tools.adahandle.resolver.repository;

import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdaHandleHistoryRepository extends JpaRepository<AdaHandleHistoryItem, String> {

    @Modifying
    @Query("DELETE FROM AdaHandleHistoryItem WHERE slot > :target")
    void deleteAllAfterSlot(@Param("target") long target);

    @Query("SELECT item FROM AdaHandleHistoryItem item WHERE (name, slot) IN (SELECT name, MAX(slot) AS max_slot FROM AdaHandleHistoryItem GROUP BY name)")
    List<AdaHandleHistoryItem> getLatestHistoryItemByName();
}
