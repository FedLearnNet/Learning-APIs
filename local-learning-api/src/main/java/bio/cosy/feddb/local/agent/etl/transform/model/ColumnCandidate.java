package bio.cosy.feddb.local.agent.etl.transform.model;

import lombok.Data;

@Data
public class ColumnCandidate {
    private String column;
    private double score;
    private double nameScore;
    private double semanticScore = 0;
    private double validRatio;
    private boolean direct;
    private String summary;

    public void calculateAndSetScore(double textWeight, double semanticBaseline) {
        this.score = textWeight * getTextScore(semanticBaseline) + (1 - textWeight) * this.getValidRatio();
    }

    public double getTextScore(double semanticBaseline) {
        double semantic = Math.max(0, (this.getSemanticScore() - semanticBaseline) / (1 - semanticBaseline));
        return Math.max(this.getNameScore(), semantic);
    }

    public void setDirect(double runnerUpScore, double semanticBaseline, double confidentText, double confidentMargin) {
        if (this.getValidRatio() < 1) {
            setDirect(false);
            return;
        }
        if (this.getNameScore() == 1) {
            setDirect(true);
            return;
        }
        setDirect(this.getTextScore(semanticBaseline) >= confidentText
                && this.getScore() - runnerUpScore >= confidentMargin);
    }
}
