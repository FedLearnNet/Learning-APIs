package bio.cosy.feddb.local.agent.etl.transform.guardrails;


public class AnswerRejectedException extends RuntimeException {

    public AnswerRejectedException(String feedback) {
        super(feedback);
    }
}
