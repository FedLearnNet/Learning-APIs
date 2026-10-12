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
    private boolean nameMatch;
    private String summary;

    public ColumnCandidate withNote(String note) {
        ColumnCandidate copy = new ColumnCandidate();
        copy.setColumn(column);
        copy.setScore(score);
        copy.setNameScore(nameScore);
        copy.setSemanticScore(semanticScore);
        copy.setValidRatio(validRatio);
        copy.setDirect(direct);
        copy.setNameMatch(nameMatch);
        copy.setSummary(summary == null ? note : summary + ", " + note);
        return copy;
    }

    public void calculateAndSetScore(double textWeight, double semanticBaseline) {
        this.score = textWeight * getTextScore(semanticBaseline) + (1 - textWeight) * this.getValidRatio();
    }

    public double getTextScore(double semanticBaseline) {
        double semantic = Math.max(0, (this.getSemanticScore() - semanticBaseline) / (1 - semanticBaseline));
        return Math.max(this.getNameScore(), semantic);
    }

    public void setNameMatch(double runnerUpText, double semanticBaseline, double confidentText, double confidentMargin) {
        double text = getTextScore(semanticBaseline);
        setNameMatch(text >= confidentText && text - runnerUpText >= confidentMargin);
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
