package com.talleres360.orders.dto;

import java.util.Map;

public record EstadoStockResponse(long revision, long confirmedRevision, boolean pending,
                                  Map<Long, Integer> quantities) {}
