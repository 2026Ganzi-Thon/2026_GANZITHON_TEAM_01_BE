package com.som.engine.data;

/**
 * engine(B) 쪽 처리물 삭제 계약(A_SPEC §11·§12). B API 계약의 `DELETE /v1/subjects/{subjectKey}`에 대응한다.
 * 같은 subjectKey로 여러 번 불러도 결과가 같아야 한다(멱등). A는 EngineGateway를 거쳐 호출한다.
 */
public interface EngineDataEraser {
    void eraseSubject(String subjectKey);
}
