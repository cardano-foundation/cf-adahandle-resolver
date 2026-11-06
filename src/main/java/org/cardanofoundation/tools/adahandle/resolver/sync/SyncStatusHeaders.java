package org.cardanofoundation.tools.adahandle.resolver.sync;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Adds the sync status to every API response, so a client can tell how current an answer is (#27): a handle resolved
 * from out-of-date data may point at an address that no longer holds it.
 */
@Component
@RequiredArgsConstructor
public class SyncStatusHeaders implements HandlerInterceptor {

    public static final String STATUS = "X-Sync-Status";
    public static final String SLOT = "X-Sync-Slot";
    public static final String BLOCK = "X-Sync-Block";
    public static final String BLOCK_TIME = "X-Sync-Block-Time";
    public static final String TIP_SLOT = "X-Sync-Tip-Slot";
    public static final List<String> ALL = List.of(STATUS, SLOT, BLOCK, BLOCK_TIME, TIP_SLOT);

    private final SyncStatusProvider provider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SyncStatusSnapshot status = provider.current();
        response.setHeader(STATUS, status.state().headerValue());
        if (status.slot() != null) {
            response.setHeader(SLOT, status.slot().toString());
        }
        if (status.block() != null) {
            response.setHeader(BLOCK, status.block().toString());
        }
        if (status.blockTime() != null) {
            response.setHeader(BLOCK_TIME, status.blockTime().toString());
        }
        if (status.tipSlot() != null) {
            response.setHeader(TIP_SLOT, status.tipSlot().toString());
        }
        return true;
    }
}
