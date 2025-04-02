package com.scalar.bugramaai.repository;

import com.scalar.bugramaai.model.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugRepository extends JpaRepository<BugReport, Long> { }
