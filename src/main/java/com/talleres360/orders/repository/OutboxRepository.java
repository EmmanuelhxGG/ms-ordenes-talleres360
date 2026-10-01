package com.talleres360.orders.repository;

import com.talleres360.orders.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop100ByReportSentFalseOrStockSentFalseOrderByOccurredAtAsc();
}
