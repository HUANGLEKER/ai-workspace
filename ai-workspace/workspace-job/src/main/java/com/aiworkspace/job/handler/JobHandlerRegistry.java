package com.aiworkspace.job.handler;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Indexes all {@link JobHandler} beans by name so jobs can resolve their target at runtime. */
@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlerBeans) {
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(JobHandler::getName, Function.identity()));
    }

    public Optional<JobHandler> find(String name) {
        return Optional.ofNullable(handlers.get(name));
    }

    /** names of all registered handlers, for the management UI dropdown */
    public List<String> names() {
        return handlers.keySet().stream().sorted().collect(Collectors.toList());
    }
}
