package com.som.routine.repository;

import com.som.routine.domain.RoutineCatalogItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RoutineCatalogRepository extends Repository<RoutineCatalogItem, String> {

    List<RoutineCatalogItem> findAllByOrderByDisplayOrder();

    Optional<RoutineCatalogItem> findById(String routineId);
}
