package org.cardanofoundation.tools.adahandle.resolver.repository;

import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdaHandleRepository extends JpaRepository<AdaHandle, String> {

    @Query("SELECT stakeAddress FROM AdaHandle WHERE name = :adaHandle")
    List<String> findStakeAddressByAdaHandle(@Param("adaHandle") String adaHandle);
}
