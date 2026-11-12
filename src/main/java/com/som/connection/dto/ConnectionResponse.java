package com.som.connection.dto;

import com.som.connection.domain.ConnectionStatus;
import com.som.connection.domain.DataSource;
import java.time.OffsetDateTime;
import java.util.List;

/** 소스별 연결 상태 + 항목별 동의 시트 고지 내용(서버 설정값). connectedAt은 CONNECTED일 때만 값이 있다. */
public record ConnectionResponse(DataSource source, ConnectionStatus status, boolean sensitive, List<String> items,
                                 String purpose, String retention, String policyVersion,
                                 OffsetDateTime connectedAt) {}
