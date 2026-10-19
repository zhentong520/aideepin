package com.moyz.adi.common.base;

import com.moyz.adi.common.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;

/**
 * 把提交任务时的请求上下文传递到异步线程。
 * <p>
 * Propagates the request context (current user, token, external-api flags) to the async thread
 * that runs the task. The context is always cleared in a finally block: pooled threads are
 * reused, so a leftover identity would leak into an unrelated request.
 * </p>
 */
@Slf4j
public class ThreadContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        User user = ThreadContext.getCurrentUser();
        String token = ThreadContext.getToken();
        boolean extApiRequest = ThreadContext.isExtApiRequest();
        String extApiEntityUuid = ThreadContext.getExtApiEntityUuid();
        String extApiEntityType = ThreadContext.getExtApiEntityType();
        return () -> {
            try {
                if (null != user) {
                    ThreadContext.setCurrentUser(user);
                }
                if (null != token) {
                    ThreadContext.setToken(token);
                }
                ThreadContext.setExtApiContext(extApiRequest, extApiEntityUuid, extApiEntityType);
                runnable.run();
            } finally {
                //Never let a pooled thread keep the identity of the previous task
                ThreadContext.unload();
            }
        };
    }
}
