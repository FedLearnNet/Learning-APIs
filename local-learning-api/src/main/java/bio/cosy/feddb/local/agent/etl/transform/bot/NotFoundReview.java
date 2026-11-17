package bio.cosy.feddb.local.agent.etl.transform.bot;

import dev.langchain4j.model.output.structured.Description;

public enum NotFoundReview {
    @Description("The reason is fine, e.g. the value has to be computed from several columns or rows")
    OK,
    @Description("The reason asks the user something or waits for the user's input")
    ASKS_USER,
    @Description("The value only translates single values of one column onto the allowed values")
    BUILT_IN
}
