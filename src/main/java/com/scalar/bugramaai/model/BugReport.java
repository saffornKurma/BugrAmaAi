package com.scalar.bugramaai.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class BugReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;

    @Column(length = 255)  // Ensures database constraint
    private String resolution;

    public void setResolution(String response) {
        this.resolution = (response.length() > 255) ? response.substring(0, 255) : response;
    }

    public String getResolution() {
        return resolution;
    }
}