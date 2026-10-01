package com.talleres360.orders.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(List<LocalDate> occupiedDates) {}
