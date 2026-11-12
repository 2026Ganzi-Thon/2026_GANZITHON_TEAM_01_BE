package com.som.chat.repository;

import com.som.chat.domain.ChatMessage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 대화 메시지 저장소. 모든 조회는 userId 조건을 포함한다. */
public interface ChatMessageRepository extends Repository<ChatMessage, Long> {

    ChatMessage saveAndFlush(ChatMessage message);

    /** 날짜별 대화, 커서(afterId) 다음부터 오래된 순. */
    List<ChatMessage> findByUserIdAndThreadDateAndIdGreaterThanOrderByIdAsc(Long userId, LocalDate threadDate,
                                                                          Long afterId, Pageable pageable);

    /** 최근 메시지(최신 순). engine 입력용. */
    List<ChatMessage> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Optional<ChatMessage> findByIdAndUserId(Long id, Long userId);

    Optional<ChatMessage> findByUserIdAndThreadDateAndGreetingTrue(Long userId, LocalDate threadDate);

    @Query("SELECT DISTINCT m.threadDate FROM ChatMessage m WHERE m.userId = :userId "
            + "AND m.threadDate BETWEEN :from AND :to ORDER BY m.threadDate")
    List<LocalDate> findThreadDates(@Param("userId") Long userId, @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
