package com.som.connection.domain;

/** 연결 상태. NOT_CONNECTED는 connections 행이 없다는 뜻으로 API에서만 쓰고 저장하지 않는다(DB CHECK). */
public enum ConnectionStatus {
    NOT_CONNECTED,
    CONNECTED,
    REVOKED
}
