package unit.importer;

import bio.cosy.feddb.local.api.importer.run.patientlog.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunErrorLogBOTest {

    private ConnectorRunPatientLogBO bo;
    private MockRunErrorLogAO mockAO;
    private MockRunErrorLogMapper mockMapper;

    @BeforeEach
    void setUp() throws Exception {
        bo = new ConnectorRunPatientLogBO();
        mockAO = new MockRunErrorLogAO();
        mockMapper = new MockRunErrorLogMapper();
        inject(bo, "ao", mockAO);
        inject(bo, "mapper", mockMapper);
    }

    @Test
    void getByRunIdReturnsAllLogs() {
        mockAO.addLog(1L, "Error 1", ConnectorRunPatientLogType.EXTRACTING, null);
        mockAO.addLog(1L, "Error 2", ConnectorRunPatientLogType.MAPPING, "Patient1");

        List<ConnectorRunPatientLogDTO> result = bo.getByRunId(1L, null, null);

        assertEquals(2, result.size());
    }

    @Test
    void getByRunIdFiltersLogType() {
        mockAO.addLog(1L, "Loading error", ConnectorRunPatientLogType.EXTRACTING, null);
        mockAO.addLog(1L, "Mapping error", ConnectorRunPatientLogType.MAPPING, null);

        List<ConnectorRunPatientLogDTO> result = bo.getByRunId(1L, "EXTRACTING", null);

        assertEquals(1, result.size());
        assertEquals("Loading error", result.get(0).getMessage());
    }

    @Test
    void getByRunIdFiltersPatientYes() {
        mockAO.addLog(1L, "Patient error", ConnectorRunPatientLogType.LOADING, "P001");
        mockAO.addLog(1L, "General error", ConnectorRunPatientLogType.LOADING, null);

        List<ConnectorRunPatientLogDTO> result = bo.getByRunId(1L, null, "yes");

        assertEquals(1, result.size());
        assertEquals("Patient error", result.get(0).getMessage());
    }

    @Test
    void getByRunIdFiltersPatientNo() {
        mockAO.addLog(1L, "Patient error", ConnectorRunPatientLogType.LOADING, "P001");
        mockAO.addLog(1L, "General error", ConnectorRunPatientLogType.LOADING, null);

        List<ConnectorRunPatientLogDTO> result = bo.getByRunId(1L, null, "no");

        assertEquals(1, result.size());
        assertEquals("General error", result.get(0).getMessage());
    }

    @Test
    void getByRunIdHandlesInvalidLogType() {
        mockAO.addLog(1L, "Error", ConnectorRunPatientLogType.EXTRACTING, null);

        List<ConnectorRunPatientLogDTO> result = bo.getByRunId(1L, "INVALID", null);

        // Invalid type should be treated as no filter
        assertEquals(1, result.size());
    }

    @Test
    void getByRunIdCombinesTypeAndPatientFilter() {
        mockAO.addLog(1L, "Patient LOADING", ConnectorRunPatientLogType.LOADING, "P001");
        mockAO.addLog(1L, "General LOADING", ConnectorRunPatientLogType.LOADING, null);
        mockAO.addLog(1L, "Patient MAPPING", ConnectorRunPatientLogType.MAPPING, "P002");

        List<ConnectorRunPatientLogDTO> result = bo.getByRunId(1L, "LOADING", "yes");

        assertEquals(1, result.size());
        assertEquals("Patient LOADING", result.get(0).getMessage());
    }

    // --- helpers ---

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + target.getClass().getName());
    }

    // --- Mocks ---

    static class MockRunErrorLogAO extends ConnectorRunPatientLogAO {
        private final List<ConnectorRunPatientLogEntity> entities = new ArrayList<>();
        private long nextId = 1L;

        void addLog(Long runId, String message, ConnectorRunPatientLogType logType, String patientId) {
            ConnectorRunPatientLogEntity entity = new ConnectorRunPatientLogEntity();
            entity.setId(nextId++);
            entity.setMessage(message);
            entity.setLogType(logType);
            entity.setPatientId(patientId);
            entity.setLevel("ERROR");
            // We mock the run relationship by checking the runId in filter methods
            bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity run =
                    new bio.cosy.feddb.local.api.importer.run.ConnectorRunEntity();
            run.setId(runId);
            entity.setRun(run);
            entities.add(entity);
        }

        @Override
        public List<ConnectorRunPatientLogEntity> findByRunId(Long runId) {
            return entities.stream()
                    .filter(e -> e.getRun().getId().equals(runId))
                    .toList();
        }

        @Override
        public List<ConnectorRunPatientLogEntity> findByRunIdAndType(Long runId, ConnectorRunPatientLogType logType) {
            return entities.stream()
                    .filter(e -> e.getRun().getId().equals(runId) && e.getLogType() == logType)
                    .toList();
        }

        @Override
        public List<ConnectorRunPatientLogEntity> findByRunIdWithPatient(Long runId) {
            return entities.stream()
                    .filter(e -> e.getRun().getId().equals(runId)
                            && e.getPatientId() != null
                            && !"Unknown".equals(e.getPatientId()))
                    .toList();
        }

        @Override
        public List<ConnectorRunPatientLogEntity> findByRunIdWithoutPatient(Long runId) {
            return entities.stream()
                    .filter(e -> e.getRun().getId().equals(runId)
                            && (e.getPatientId() == null || "Unknown".equals(e.getPatientId())))
                    .toList();
        }

        @Override
        public List<ConnectorRunPatientLogEntity> findByRunIdAndTypeWithPatient(Long runId, ConnectorRunPatientLogType logType) {
            return entities.stream()
                    .filter(e -> e.getRun().getId().equals(runId)
                            && e.getLogType() == logType
                            && e.getPatientId() != null
                            && !"Unknown".equals(e.getPatientId()))
                    .toList();
        }

        @Override
        public List<ConnectorRunPatientLogEntity> findByRunIdAndTypeWithoutPatient(Long runId, ConnectorRunPatientLogType logType) {
            return entities.stream()
                    .filter(e -> e.getRun().getId().equals(runId)
                            && e.getLogType() == logType
                            && (e.getPatientId() == null || "Unknown".equals(e.getPatientId())))
                    .toList();
        }
    }

    static class MockRunErrorLogMapper implements ConnectorRunPatientLogMapper {
        @Override
        public ConnectorRunPatientLogDTO entityToDto(ConnectorRunPatientLogEntity entity) {
            ConnectorRunPatientLogDTO dto = new ConnectorRunPatientLogDTO();
            dto.setId(entity.getId());
            dto.setMessage(entity.getMessage());
            dto.setLogType(entity.getLogType());
            dto.setPatientId(entity.getPatientId());
            dto.setField(entity.getField());
            dto.setLevel(entity.getLevel());
            dto.setRunId(entity.getRun() != null ? entity.getRun().getId() : null);
            return dto;
        }

        @Override
        public ConnectorRunPatientLogEntity dtoToEntity(ConnectorRunPatientLogDTO dto) {
            ConnectorRunPatientLogEntity entity = new ConnectorRunPatientLogEntity();
            entity.setId(dto.getId());
            entity.setMessage(dto.getMessage());
            entity.setLogType(dto.getLogType());
            entity.setPatientId(dto.getPatientId());
            entity.setField(dto.getField());
            entity.setLevel(dto.getLevel());
            return entity;
        }

        @Override
        public List<ConnectorRunPatientLogDTO> entitiesToDtos(List<ConnectorRunPatientLogEntity> entities) {
            return entities.stream().map(this::entityToDto).toList();
        }

        @Override
        public List<ConnectorRunPatientLogEntity> dtosToEntities(List<ConnectorRunPatientLogDTO> dtos) {
            return dtos.stream().map(this::dtoToEntity).toList();
        }

        @Override
        public List<ConnectorRunPatientLogDTO> entitiesToDtos(java.util.stream.Stream<ConnectorRunPatientLogEntity> entity) {
            return entity.map(this::entityToDto).toList();
        }

        @Override
        public List<ConnectorRunPatientLogDTO> entitiesToDtos(java.util.Set<ConnectorRunPatientLogEntity> entity) {
            return entity.stream().map(this::entityToDto).toList();
        }

        @Override
        public List<ConnectorRunPatientLogEntity> dtosToEntities(java.util.stream.Stream<ConnectorRunPatientLogDTO> dto) {
            return dto.map(this::dtoToEntity).toList();
        }
    }
}
