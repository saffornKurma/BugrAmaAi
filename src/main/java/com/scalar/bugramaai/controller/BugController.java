package com.scalar.bugramaai.controller;

import com.scalar.bugramaai.model.BugReport;
import com.scalar.bugramaai.repository.BugRepository;
import com.scalar.bugramaai.service.AIService;
import com.scalar.bugramaai.service.BugService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000")  // Allow frontend requests
@RestController
@RequestMapping("/api/bugs")
public class BugController {
    private final BugRepository bugRepository;
    private final AIService aiService;
    private final BugService bugService;

    public BugController(BugRepository bugRepository, AIService aiService, BugService bugService) {
        this.bugRepository = bugRepository;
        this.aiService = aiService;
        this.bugService = bugService;
    }

    @PostMapping("/report")
    public ResponseEntity<BugReport> reportBug(@RequestBody BugReport bugReport) {
        // AI-generated resolution (limited to 255 characters)
        String aiResolution = aiService.getAIResolution(bugReport.getDescription());
        bugReport.setResolution(aiResolution);

        // Save bug report
        BugReport savedBug = bugRepository.save(bugReport);
        return ResponseEntity.ok(savedBug);
    }

    /**
     * 📌 **Resolve an existing bug**
     * - Fetches bug by ID and updates its resolution.
     */
    @PutMapping("/{bugId}/resolve")
    public ResponseEntity<BugReport> resolveBug(@PathVariable Long bugId) {
        BugReport updatedBug = bugService.resolveBug(bugId);
        return ResponseEntity.ok(updatedBug);
    }

    /**
     * 📌 **Retrieve all bug reports**
     * - Returns a list of all bugs stored in the database.
     */
    @GetMapping
    public ResponseEntity<List<BugReport>> getAllBugs() {
        List<BugReport> bugs = bugRepository.findAll();
        return ResponseEntity.ok(bugs);
    }
}
