package com.som.settings.repository;

import com.som.settings.domain.HelpResource;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface HelpResourceRepository extends Repository<HelpResource, Long> {

    List<HelpResource> findAllByOrderByDisplayOrder();
}
