package com.som.engine.chat;

/** 최근 대화 한 턴. 원문 전송이 꺼져 있으면 USER의 text는 null이다. */
public record ChatTurn(ChatRole role, String text) {

    @Override
    public String toString() {
        return "ChatTurn[role=" + role + "]";
    }
}
