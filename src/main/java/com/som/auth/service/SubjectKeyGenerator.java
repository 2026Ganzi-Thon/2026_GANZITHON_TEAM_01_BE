package com.som.auth.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** engine용 가명 ID 발급. 무작위 UUID이며 userId·loginId에서 유도하지 않는다(A_SPEC §12). */
@Component
public class SubjectKeyGenerator {

    public String next() {
        return UUID.randomUUID().toString();
    }
}
