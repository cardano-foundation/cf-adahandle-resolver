package org.cardanofoundation.tools.adahandle.resolver.service;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.projection.Addresses;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdaHandleService {
    @Autowired
    private AdaHandleRepository adaHandleRepository;

    public void saveAll(List<AdaHandle> adaHandles) {
        adaHandleRepository.saveAll(adaHandles);
    }

    public @Nullable Addresses getAddressesByAdaHandle(String adaHandle) {
        if (adaHandle.startsWith("$") && adaHandle.length() > 1) {
            adaHandle = adaHandle.substring(1);
        }

        return adaHandleRepository.findAddressesByAdaHandle(adaHandle);
    }

    public List<String> getAdaHandlesByStakeAddress(String stakeAddress) {
        return adaHandleRepository.findAdaHandlesByStakeAddress(stakeAddress);
    }

    public List<String> getAdaHandlesByPaymentAddress(String paymentAddress) {
        return adaHandleRepository.findAdaHandlesByPaymentAddress(paymentAddress);
    }

    /** Upsert a single handle row (name is the @Id, so save() merges). */
    public void upsert(AdaHandle adaHandle) {
        adaHandleRepository.save(adaHandle);
    }

    /** Delete handles by name (primary key), e.g. burned ones. Names without a row are skipped. */
    public void deleteAllByName(List<String> names) {
        adaHandleRepository.deleteAllById(names);
    }

    /** Delete a single handle by its name (primary key). No-op if the row doesn't exist. */
    public void deleteByName(String name) {
        adaHandleRepository.deleteById(name);
    }
}
