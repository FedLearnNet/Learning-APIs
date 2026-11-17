package bio.cosy.feddb.local.agent.etl.transform.model;

import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import dev.langchain4j.agentic.declarative.TypedKey;

public class PreviousValidation implements TypedKey<ConnectorValidationResultDTO> {
    @Override
    public ConnectorValidationResultDTO defaultValue() {
        return new ConnectorValidationResultDTO("none, this is the first attempt");
    }

    @Override
    public String name() {
        return "validation";
    }
}
