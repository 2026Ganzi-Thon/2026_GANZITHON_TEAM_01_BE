package com.som.recovery.service;

import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.common.time.ServiceDates;
import com.som.recovery.dto.RecoveryShareResponse;
import com.som.recovery.repository.RecoveryShareRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** 회복 팁 익명 공유. 글 원문은 로그·예외 메시지에 넣지 않는다. */
@Service
public class RecoveryShareService {

    static final int LIST_SIZE = 30;

    private final RecoveryShareRepository shares;
    private final ServiceDates dates;

    public RecoveryShareService(RecoveryShareRepository shares, ServiceDates dates) {
        this.shares = shares;
        this.dates = dates;
    }

    public List<RecoveryShareResponse> recent(Long userId) {
        return shares.recent(userId, LIST_SIZE);
    }

    /** 앞뒤 공백을 지우고 한 줄만 받는다(제어문자·줄바꿈은 400). */
    public RecoveryShareResponse share(Long userId, String message) {
        String text = message.strip();
        if (text.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "message: 한 줄로 작성해 주세요");
        }
        long id = shares.insert(userId, text, dates.now());
        return new RecoveryShareResponse(id, text, true);
    }

    public void remove(Long userId, long id) {
        if (!shares.deleteOwn(userId, id)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
