package com.winten.greenlight.prototype.core.api.controller.queue;

import lombok.Data;

@Data
public class QueueInfoDTO {
    private String queueId;
    private String actionId;
    private long score;
    private String token;
    private boolean isActive;
}
