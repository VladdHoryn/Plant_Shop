package com.plantshop.user.repository;

import com.plantshop.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderById(Long userId);

    @Modifying
    @Query("delete from Address a where a.userId = :userId")
    int deleteByUserId(Long userId);
}
