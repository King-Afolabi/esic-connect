package com.esic.connect.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

interface EmailDeliveryRepository
        extends JpaRepository<EmailDelivery, Long>, JpaSpecificationExecutor<EmailDelivery> {

    List<EmailDelivery> findByUserIdOrderByCreatedAtDesc(Long userId);
}
