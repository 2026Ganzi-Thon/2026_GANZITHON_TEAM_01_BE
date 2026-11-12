package com.som.checkin.dto;

import com.som.checkin.domain.CheckinStatus;
import com.som.checkin.domain.Mood;
import com.som.checkin.domain.Stressor;
import com.som.checkin.domain.SupportNeed;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CheckinResponse(LocalDate localDate, Mood mood, List<Stressor> stressors, SupportNeed supportNeed,
                              CheckinStatus status, OffsetDateTime updatedAt) {}
