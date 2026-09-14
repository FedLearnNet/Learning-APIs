package bio.cosy.feddb.local.api.notification;

import bio.cosy.feddb.core.api.notification.CreateNotificationDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface NotificationMapper extends BaseMapper<NotificationDTO, NotificationEntity> {

    NotificationDTO entityToDto(NotificationEntity entity);

    NotificationEntity dtoToEntity(NotificationDTO dto);

    @Mappings({
            @Mapping(target = "archivedAt", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "deliveredAt", ignore = true),
            @Mapping(target = "deliveredFailedAt", ignore = true),
            @Mapping(target = "deliveryAttemptCount", ignore = true),
            @Mapping(target = "deliveryErrorMessage", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "readAt", ignore = true),
            @Mapping(target = "recipientUserId", ignore = true),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true)
    })
    NotificationEntity createEntityToEntity(CreateNotificationDTO createNotificationDTO);

}
