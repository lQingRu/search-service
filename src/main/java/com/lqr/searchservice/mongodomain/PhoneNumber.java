package com.lqr.searchservice.mongodomain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
public class PhoneNumber {

    private String personId;
    private String deviceId;
    private String value;
    private LocalDateTime firstCreated;
    private LocalDateTime lastUsed;
}
