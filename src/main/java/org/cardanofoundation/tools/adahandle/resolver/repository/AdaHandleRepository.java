package org.cardanofoundation.tools.adahandle.resolver.repository;

import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.projection.Addresses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdaHandleRepository extends JpaRepository<AdaHandle, String> {

    @Query("SELECT handle.stakeAddress AS stakeAddress , handle.paymentAddress AS paymentAddress FROM AdaHandle " +
            "AS handle WHERE handle.name = :adaHandle")
    Addresses findAddressesByAdaHandle(@Param("adaHandle") String adaHandle);

    @Query("SELECT name FROM AdaHandle WHERE stakeAddress = :stakeAddress")
    List<String> findAdaHandlesByStakeAddress(@Param("stakeAddress") String stakeAddress);

    @Query("SELECT name FROM AdaHandle WHERE paymentAddress = :paymentAddress")
    List<String> findAdaHandlesByPaymentAddress(@Param("paymentAddress") String paymentAddress);
}
