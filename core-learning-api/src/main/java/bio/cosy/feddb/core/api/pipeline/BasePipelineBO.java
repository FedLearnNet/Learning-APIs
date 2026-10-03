package bio.cosy.feddb.core.api.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseMapper;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.Set;

public abstract class BasePipelineBO<Dto extends BasePipelineDTO, Entity extends BasePipelineEntity<?>,
        Ao extends BasePipelineAO<Entity>, Mapper extends BaseMapper<Dto, Entity>> extends BaseBo<Dto, Entity, Ao, Mapper> {

    private static final Set<PipelineStatus> FINISHED = Set.of(PipelineStatus.SUCCESS, PipelineStatus.FAILED);

    protected final BroadcastProcessor<Dto> updates = BroadcastProcessor.create();

    protected abstract PipelineRunInfoDTO runInfo(Entity pipeline);

    protected abstract byte[] files(Entity pipeline);

    protected abstract void finished(Entity pipeline);

    @Transactional
    public PipelineRunInfoDTO getPipelineRunInfo(Long id, String secret) {
        return runInfo(ao.findByIdAndSecret(id, secret, LockModeType.PESSIMISTIC_READ));
    }

    @Transactional
    public byte[] getFiles(Long id, String secret) {
        return files(ao.findByIdAndSecret(id, secret, LockModeType.PESSIMISTIC_READ));
    }

    @Transactional
    public void processStatusUpdate(Long id, String secret, PipelineStatusUpdateDTO dto) {
        apply(ao.findByIdAndSecret(id, secret, LockModeType.PESSIMISTIC_WRITE), dto);
    }

    @Transactional
    public void processSummary(Long id, String secret, AppPublishInfoDTO dto) {
        Entity pipeline = ao.findByIdAndSecret(id, secret, LockModeType.PESSIMISTIC_WRITE);
        pipeline.setPublishInfo(dto);
        updates.onNext(mapper.entityToDto(pipeline));
    }

    protected void apply(Entity pipeline, PipelineStatusUpdateDTO dto) {
        PipelineStatus before = pipeline.getPipelineStatus();
        pipeline.apply(dto);
        if (before != pipeline.getPipelineStatus() && FINISHED.contains(pipeline.getPipelineStatus())) {
            finished(pipeline);
        }
        updates.onNext(mapper.entityToDto(pipeline));
    }
}
