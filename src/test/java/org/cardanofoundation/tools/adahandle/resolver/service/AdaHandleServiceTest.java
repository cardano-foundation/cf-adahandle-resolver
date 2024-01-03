package org.cardanofoundation.tools.adahandle.resolver.service;

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
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(0L,"Max","stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z","addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls", 1000L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(1L,"Tom", "stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p","addr1u87ua2cf830jqwa3s59ds35pe4jnhupmlwdk3uvpr2mv2xc3x6h7p", 1200L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(2L,"Otto", "stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p","addr1u87ua2cf830jqwa3s59drvrt3gko5rvdrtf5pr2mv2xc3x6h7p", 1202L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(1L,"Tom", "stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z","addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls", 1305L));
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
    public void testDollarSign() {
        Addresses addresses = adaHandleService.getAddressesByAdaHandle("$Tom");
        assertThat(addresses.getStakeAddress(), equalTo("stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z"));
        addresses = adaHandleService.getAddressesByAdaHandle("Tom");
        assertThat(addresses.getStakeAddress(), equalTo("stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z"));
        addresses = adaHandleService.getAddressesByAdaHandle("$");
        assertThat(addresses, equalTo(null));
    }
}
