package com.motorbike.trip;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TripForm {

    @NotBlank(message = "Dai un nome al viaggio")
    @Size(max = 120)
    private String title;

    @Size(max = 2000)
    private String description;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    static TripForm from(Trip t) {
        TripForm f = new TripForm();
        f.title = t.getTitle();
        f.description = t.getDescription();
        f.startDate = t.getStartDate();
        return f;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
}
