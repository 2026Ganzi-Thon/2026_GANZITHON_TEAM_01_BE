package com.som.settings.service;

import com.som.settings.dto.HelpResourceResponse;
import com.som.settings.repository.HelpResourceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 도움 받을 곳 목록. 마이 화면과 대화 위기 응답이 같이 쓴다. */
@Service
public class HelpResourceService {

    private final HelpResourceRepository resources;

    public HelpResourceService(HelpResourceRepository resources) {
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public List<HelpResourceResponse> all() {
        return resources.findAllByOrderByDisplayOrder().stream()
                .map(r -> new HelpResourceResponse(r.getSituation(), r.getName(), r.getDescription(),
                        r.getActionType(), r.getActionValue()))
                .toList();
    }
}
