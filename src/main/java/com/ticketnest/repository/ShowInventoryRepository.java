package com.ticketnest.repository;

import com.ticketnest.entity.ShowInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowInventoryRepository extends JpaRepository<ShowInventory, UUID> {
}
