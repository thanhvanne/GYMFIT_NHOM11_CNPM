package com.gymfit.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymfit.common.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.gymfit.audit.dto.AuditEventResponse;
import java.util.List;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public List<AuditEventResponse> list(Long branchId) {
        List<AuditEvent> events = branchId == null
                ? auditEventRepository
                .findAllByOrderByCreatedAtUtcDesc()
                : auditEventRepository
                .findAllByBranchIdOrderByCreatedAtUtcDesc(
                        branchId
                );

        return events.stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditEventResponse toResponse(
            AuditEvent event
    ) {
        return new AuditEventResponse(
                event.getId(),
                event.getActorUserId(),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getBranchId(),
                event.getDetailsJson(),
                event.getCreatedAtUtc()
        );
    }

    public AuditEvent record(
            Long actorUserId,
            String action,
            String entityType,
            Long entityId,
            Long branchId,
            Map<String, ?> details
    ) {
        String detailsJson = null;

        if (details != null && !details.isEmpty()) {
            try {
                detailsJson =
                        objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "Cannot serialize audit details",
                        exception
                );
            }
        }

        AuditEvent event = AuditEvent.builder()
                .actorUserId(actorUserId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .branchId(branchId)
                .detailsJson(detailsJson)
                .createdAtUtc(TimeUtil.now())
                .build();

        return auditEventRepository.save(event);
    }
}