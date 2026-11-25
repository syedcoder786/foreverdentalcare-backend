package com.clinic.repository;

import com.clinic.entity.NavigationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NavigationItemRepository extends JpaRepository<NavigationItem, Long> {

    List<NavigationItem> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<NavigationItem> findAllByOrderByDisplayOrderAscIdAsc();
}
