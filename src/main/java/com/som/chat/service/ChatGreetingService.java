package com.som.chat.service;

import com.som.chat.domain.ChatMessage;
import com.som.chat.repository.ChatMessageRepository;
import com.som.common.time.ServiceDates;
import com.som.engine.chat.ChatMode;
import com.som.engine.chat.ChatResult;
import com.som.state.service.GreetingQuery;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루 1회 선제 인사(A_SPEC §7 기본값). 오늘 대화를 열 때 없으면 engine GREETING으로 만들어 저장한다.
 * engine 실패면 저장하지 않는다(고정 문구로 채우지 않고 다음 조회 때 다시 시도). 홈 말풍선은 저장된 인사만 읽는다.
 */
@Service
public class ChatGreetingService implements GreetingQuery {

    private final ChatMessageRepository messages;
    private final ChatRequestFactory requests;
    private final ChatService chatService;
    private final ChatReplyWriter writer;
    private final ServiceDates dates;

    public ChatGreetingService(ChatMessageRepository messages, ChatRequestFactory requests, ChatService chatService,
                               ChatReplyWriter writer, ServiceDates dates) {
        this.messages = messages;
        this.requests = requests;
        this.chatService = chatService;
        this.writer = writer;
        this.dates = dates;
    }

    public void ensureToday(Long userId) {
        LocalDate today = dates.today();
        if (messages.findByUserIdAndThreadDateAndGreetingTrue(userId, today).isPresent()) {
            return;
        }
        Optional<ChatResult> result = chatService.respond(requests.build(userId, today, ChatMode.GREETING, null));
        result.ifPresent(r -> writer.saveGreetingIfAbsent(userId, today, r, dates.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> greetingOn(Long userId, LocalDate date) {
        return messages.findByUserIdAndThreadDateAndGreetingTrue(userId, date).map(ChatMessage::getContent);
    }
}
