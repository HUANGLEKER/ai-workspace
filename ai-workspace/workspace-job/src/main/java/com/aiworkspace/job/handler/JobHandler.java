package com.aiworkspace.job.handler;

/**
 * A unit of work that a scheduled {@code SysJob} can invoke. Implementations are
 * Spring beans; a job's {@code invokeTarget} column stores the handler's {@link #getName()}.
 * Add new job types by implementing this interface and annotating with {@code @Component}.
 */
public interface JobHandler {

    /** unique handler name, referenced by {@code SysJob.invokeTarget} */
    String getName();

    /**
     * Execute the job.
     *
     * @param params optional free-form parameter string configured on the job
     * @throws Exception any failure; recorded in the job log as a failed run
     */
    void execute(String params) throws Exception;
}
