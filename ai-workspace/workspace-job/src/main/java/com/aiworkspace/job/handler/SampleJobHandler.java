package com.aiworkspace.job.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Built-in demo handler so the scheduler is usable out of the box. Logs a heartbeat
 * line each time it fires; serves as a template for real job types.
 */
@Component
public class SampleJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SampleJobHandler.class);

    @Override
    public String getName() {
        return "sampleJob";
    }

    @Override
    public void execute(String params) {
        log.info("[SampleJob] heartbeat at {} params={}", LocalDateTime.now(), params);
    }
}
