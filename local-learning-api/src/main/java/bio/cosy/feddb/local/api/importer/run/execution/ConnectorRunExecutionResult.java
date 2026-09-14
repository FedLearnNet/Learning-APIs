package bio.cosy.feddb.local.api.importer.run.execution;

import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;

import java.util.LinkedHashMap;


public record ConnectorRunExecutionResult(ConnectorRunStepDTO run,
                                          TableData outputData,
                                          LinkedHashMap<String, Object> storedOutputs) {
}
