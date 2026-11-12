package com.som.settings.controller;

import com.som.settings.dto.HelpResourceResponse;
import com.som.settings.service.HelpResourceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 도움 받을 곳. 인증은 필요하지만 필수 약관 게이트 예외(위기 안내는 동의 전에도 볼 수 있어야 한다). */
@RestController
@RequestMapping("/api/v1/help-resources")
public class HelpResourceController {

    private final HelpResourceService helpResourceService;

    public HelpResourceController(HelpResourceService helpResourceService) {
        this.helpResourceService = helpResourceService;
    }

    @GetMapping
    List<HelpResourceResponse> list() {
        return helpResourceService.all();
    }
}
