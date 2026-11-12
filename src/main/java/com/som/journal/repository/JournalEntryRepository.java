package com.som.journal.repository;

import com.som.journal.domain.JournalEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 마음 정리 기록 저장소. 모든 조회는 userId 조건을 포함한다. */
public interface JournalEntryRepository extends Repository<JournalEntry, Long> {

    JournalEntry saveAndFlush(JournalEntry entry);

    List<JournalEntry> findByUserIdAndLocalDateOrderByCreatedAtAscIdAsc(Long userId, LocalDate localDate);
}
