package com.example.smartgrading.controller;

import com.example.smartgrading.model.GradeRequest;
import com.example.smartgrading.model.GradeResponse;
import com.example.smartgrading.service.GradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/grading")
@RequiredArgsConstructor
public class GradingController {

    private final GradingService gradingService;

    @PostMapping("/upload")
    public GradeResponse grade(GradeRequest request) throws Exception {
        return gradingService.processPaper(request);
    }
}
