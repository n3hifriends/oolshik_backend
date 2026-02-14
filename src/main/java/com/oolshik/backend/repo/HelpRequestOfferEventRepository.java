package com.oolshik.backend.repo;

import com.oolshik.backend.entity.HelpRequestOfferEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HelpRequestOfferEventRepository extends JpaRepository<HelpRequestOfferEventEntity, UUID> {
}
