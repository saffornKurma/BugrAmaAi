package com.scalar.bugramaai.service;

import com.scalar.bugramaai.exception.BugNotFoundException;
import com.scalar.bugramaai.model.BugReport;
import com.scalar.bugramaai.repository.BugRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BugService {
    @Autowired
    private BugRepository bugRepository;

    @Autowired
    private AIService aiService;

    public BugReport resolveBug(Long bugId) {
        BugReport bugReport = bugRepository.findById(bugId)
                .orElseThrow(() -> new BugNotFoundException("Bug not found"));

        // Get AI-generated resolution
        String aiResolution = aiService.getAIResolution(bugReport.getDescription());

        // Set resolution with length constraint
        bugReport.setResolution(aiResolution);

        return bugRepository.save(bugReport);
    }
}