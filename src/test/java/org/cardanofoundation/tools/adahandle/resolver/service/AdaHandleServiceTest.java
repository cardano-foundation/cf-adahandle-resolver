package org.cardanofoundation.tools.adahandle.resolver.service;

import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.projection.Addresses;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import java.util.ArrayList;
import java.util.List;

@DataJpaTest
@ComponentScan
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("[disable-indexer, test]")
public class AdaHandleServiceTest {

    @Autowired
    private AdaHandleService adaHandleService;
    @Autowired
    private AdaHandleHistoryService adaHandleHistoryService;

    @BeforeEach
    public void setup() {
        adaHandleHistoryService.deleteAll();
        List<AdaHandleHistoryItem> adaHandleHistoryItems = new ArrayList<>();
        adaHandleHistoryItems.add(new AdaHandleHistoryItem("Max","stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z","addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls", 1000L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem("Tom", "stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p","addr1u87ua2cf830jqwa3s59ds35pe4jnhupmlwdk3uvpr2mv2xc3x6h7p", 1200L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem("Otto", "stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p","addr1u87ua2cf830jqwa3s59drvrt3gko5rvdrtf5pr2mv2xc3x6h7p", 1202L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem("Tom", "stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z","addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls", 1305L));
        adaHandleHistoryService.saveAll(adaHandleHistoryItems);
        adaHandleService.recalculateAdaHandlesFromHistory(adaHandleHistoryItems);
    }

    @Test
    public void testGetAdaHandlesByStakeAddressBeforeRollback() {
        List<String> adaHandles = adaHandleService.getAdaHandlesByStakeAddress("stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p");
        assertThat(adaHandles.size(), equalTo(1));
        assertThat(adaHandles, hasItems("Otto"));
    }

    @Test
    public void testGetAdaHandlesByPaymentAddress() {
        List<String> adaHandles = adaHandleService.getAdaHandlesByPaymentAddress("addr1u87ua2cf830jqwa3s59drvrt3gko5rvdrtf5pr2mv2xc3x6h7p");
        assertThat(adaHandles.size(), equalTo(1));
        assertThat(adaHandles.get(0), equalTo("Otto"));
    }

    @Test
    public void testRollback() {
        Addresses addresses = adaHandleService.getAddressesByAdaHandle("Tom");
        assertThat(addresses.getStakeAddress(), equalTo("stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z"));
        assertThat(addresses.getPaymentAddress(), equalTo("addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls"));
        adaHandleHistoryService.rollbackToSlot(1202L);
        addresses = adaHandleService.getAddressesByAdaHandle("Tom");
        assertThat(addresses.getStakeAddress(), equalTo("stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p"));
        List<String> adaHandles = adaHandleService.getAdaHandlesByStakeAddress("stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p");
        assertThat(adaHandles.size(), equalTo(2));
        assertThat(adaHandles, hasItems("Tom", "Otto"));
    }

    @Test
    public void testRollbackRestoresHandleToOlderVersion() {
        // "Eve" was transferred several times. The most recent transfer (slot 3000) is on the
        // abandoned fork; the rollback must restore Eve to its immediately-preceding owner
        // (the slot-2000 version) — not the oldest (slot 1000) and not the deleted fork owner.
        adaHandleHistoryService.saveAll(List.of(
                new AdaHandleHistoryItem("Eve", "stake1eveOldA", "addr1eveOldA", 1000L),
                new AdaHandleHistoryItem("Eve", "stake1eveOldB", "addr1eveOldB", 2000L),
                new AdaHandleHistoryItem("Eve", "stake1eveNewFork", "addr1eveNewFork", 3000L)));
        adaHandleService.upsert(new AdaHandle("Eve", "stake1eveNewFork", "addr1eveNewFork"));

        // Pre-rollback Eve resolves to the fork owner.
        Addresses eve = adaHandleService.getAddressesByAdaHandle("Eve");
        assertThat(eve.getPaymentAddress(), equalTo("addr1eveNewFork"));

        // Rollback past slot 3000 only — the slot-2000 version is the one to restore to.
        adaHandleHistoryService.rollbackToSlot(2500L);

        // Eve is restored to the immediately-older (slot 2000) owner, not deleted and not the
        // oldest slot-1000 owner. This is the path exercised by findFirstByNameOrderBySlotDesc.
        eve = adaHandleService.getAddressesByAdaHandle("Eve");
        assertThat(eve, is(not(nullValue())));
        assertThat(eve.getStakeAddress(), equalTo("stake1eveOldB"));
        assertThat(eve.getPaymentAddress(), equalTo("addr1eveOldB"));

        // A deeper rollback past slot 2000 restores Eve to the oldest (slot 1000) owner,
        // proving the PK-seek re-picks the new latest after each delete.
        adaHandleHistoryService.rollbackToSlot(1500L);
        eve = adaHandleService.getAddressesByAdaHandle("Eve");
        assertThat(eve.getStakeAddress(), equalTo("stake1eveOldA"));
        assertThat(eve.getPaymentAddress(), equalTo("addr1eveOldA"));
    }

    @Test
    public void testRollbackRemovesHandleMintedOnlyOnFork() {
        // "Bob" was first minted on the abandoned fork (slot 1400), so it has no history
        // before the rollback point and must be removed from ada_handle entirely (not just
        // reverted to an earlier owner).
        adaHandleHistoryService.saveAll(List.of(
                new AdaHandleHistoryItem("Bob", "stake1bob0000", "addr1bob0000", 1400L)));
        adaHandleService.upsert(new AdaHandle("Bob", "stake1bob0000", "addr1bob0000"));

        assertThat(adaHandleService.getAddressesByAdaHandle("Bob").getPaymentAddress(), equalTo("addr1bob0000"));

        adaHandleHistoryService.rollbackToSlot(1202L);

        // Bob's fork-only history is gone and it has no earlier history → handle removed.
        assertThat(adaHandleService.getAddressesByAdaHandle("Bob"), equalTo(null));
    }

    @Test
    public void testRollbackNoOpDoesNotRecompute() {
        // A rollback to a slot with no history above it (here the current tip of the fixture,
        // slot 1305) is the no-op the node sends at the catch-up-to-tip transition. It must
        // not touch the handle table at all.
        Addresses tomBefore = adaHandleService.getAddressesByAdaHandle("Tom");
        adaHandleHistoryService.rollbackToSlot(1305L);
        Addresses tomAfter = adaHandleService.getAddressesByAdaHandle("Tom");
        assertThat(tomAfter.getStakeAddress(), equalTo(tomBefore.getStakeAddress()));
        assertThat(tomAfter.getPaymentAddress(), equalTo(tomBefore.getPaymentAddress()));
    }

    @Test
    public void testDollarSign() {
        Addresses addresses = adaHandleService.getAddressesByAdaHandle("$Tom");
        assertThat(addresses.getStakeAddress(), equalTo("stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z"));
        addresses = adaHandleService.getAddressesByAdaHandle("Tom");
        assertThat(addresses.getStakeAddress(), equalTo("stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z"));
        addresses = adaHandleService.getAddressesByAdaHandle("$");
        assertThat(addresses, equalTo(null));
    }
}
