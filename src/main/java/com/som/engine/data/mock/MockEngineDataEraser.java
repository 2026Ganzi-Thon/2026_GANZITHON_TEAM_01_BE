package com.som.engine.data.mock;

import com.som.engine.data.EngineDataEraser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Mock engine은 사용자별로 아무것도 저장하지 않으므로 지울 것이 없다. */
@Component
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockEngineDataEraser implements EngineDataEraser {

    @Override
    public void eraseSubject(String subjectKey) {
        // 저장한 처리물 없음
    }
}
