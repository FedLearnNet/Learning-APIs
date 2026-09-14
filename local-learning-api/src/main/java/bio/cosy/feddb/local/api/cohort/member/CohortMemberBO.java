package bio.cosy.feddb.local.api.cohort.member;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.services.KeycloakService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CohortMemberBO extends BaseBo<CohortMemberDTO, CohortMemberEntity, CohortMemberAO, CohortMemberMapper> {

    @Inject
    CohortAO cohortAO;

    @Inject
    CohortMemberAuthBO memberAuthBO;

    @Inject
    KeycloakService keycloakService;

    public CohortMemberDTO addMember(CohortMemberCreateDTO member, String keycloakId) {
        CohortEntity cohortEntity = cohortAO.findByIdOptional(member.getCohortId())
                .orElseThrow(() -> new NotFoundException("Cohort not found with id: " + member.getCohortId()));

        memberAuthBO.checkForEdit(cohortEntity, keycloakId);

        CohortMemberEntity memberEntity = mapper.crudDtoToEntity(member);
        memberEntity.setCohort(cohortEntity);
        ao.persist(memberEntity);
        return mapper.entityToDto(memberEntity);
    }

    public void deleteMember(Long cohortId, Long id, String keycloakId) {
        memberAuthBO.checkForDelete(cohortId, keycloakId);
        deleteById(id);
    }

    public CohortMemberDTO updateMember(CohortMemberDTO member, String keycloakId) {
        memberAuthBO.checkForEdit(member.getCohortId(), keycloakId);
        CohortMemberEntity memberEntity = ao.findByIdOptional(member.getId())
                .orElseThrow(() -> new NotFoundException("Cohort member not found with id: " + member.getId()));
        memberEntity.setType(member.getType());
        ao.persist(memberEntity);
        return mapper.entityToDto(memberEntity);
    }

    public List<CohortAvailableUserDTO> getAvailableKeycloakUsers() {
        return keycloakService.getAvailableKeycloakUsers()
                .stream()
                .map(CohortAvailableUserDTO::of)
                .toList();
    }

    public List<String> getAllKeycloakIdsForCohort(Long cohortId) {
        CohortEntity cohort = cohortAO.findByIdOptional(cohortId)
                .orElseThrow(() -> new NotFoundException("Cohort not found with id: " + cohortId));
        List<String> memberIds = new ArrayList<>(cohort.getMembers().stream()
                .map(CohortMemberEntity::getKeycloakId)
                .toList());
        memberIds.add(cohort.getKeycloakId());
        return memberIds;
    }
}
