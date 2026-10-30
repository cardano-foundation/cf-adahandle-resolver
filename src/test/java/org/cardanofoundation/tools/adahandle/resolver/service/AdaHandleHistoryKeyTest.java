package org.cardanofoundation.tools.adahandle.resolver.service;

import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItemKey;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"disable-indexer", "test"})
public class AdaHandleHistoryKeyTest {

    @Autowired
    private AdaHandleHistoryService adaHandleHistoryService;
    @Autowired
    private AdaHandleHistoryRepository adaHandleHistoryRepository;

    @Test
    public void testKeysWithTheSameNameAndSlotAreEqual() {
        AdaHandleHistoryItemKey key = new AdaHandleHistoryItemKey("alice", 1000L);

        assertThat(key, is(new AdaHandleHistoryItemKey("alice", 1000L)));
        assertThat(key.hashCode(), is(new AdaHandleHistoryItemKey("alice", 1000L).hashCode()));
        assertThat(key, not(new AdaHandleHistoryItemKey("alice", 1001L)));
        assertThat(key, not(new AdaHandleHistoryItemKey("bob", 1000L)));
    }

    @Test
    public void testHandleMovedTwiceInOneBlockKeepsTheLastOwner() {
        // Two transactions in one block move the same handle: same (name, slot), saved together.
        adaHandleHistoryService.saveAll(List.of(
                new AdaHandleHistoryItem("twiceinablock", "stake_first", "addr_first", 60_000_000L),
                new AdaHandleHistoryItem("twiceinablock", "stake_last", "addr_last", 60_000_000L)));

        AdaHandleHistoryItem latest = adaHandleHistoryRepository.findFirstByNameOrderBySlotDesc("twiceinablock");
        assertThat(latest.getPaymentAddress(), is("addr_last"));
        assertThat(adaHandleHistoryRepository.findById(new AdaHandleHistoryItemKey("twiceinablock", 60_000_000L))
                .map(AdaHandleHistoryItem::getPaymentAddress).orElse(null), is("addr_last"));
    }
}
