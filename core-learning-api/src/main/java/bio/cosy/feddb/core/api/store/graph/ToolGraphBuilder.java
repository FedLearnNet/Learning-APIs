package bio.cosy.feddb.core.api.store.graph;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolOutputConfigDTO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class ToolGraphBuilder {
    public ToolGraphDTO createGraph(List<FederatedAppDetailDTO> apps) {
        ToolGraphDTO graph = new ToolGraphDTO();
        if (apps == null || apps.isEmpty()) return graph;

        for (FederatedAppDetailDTO app : apps) {
            if (app == null) continue;
            graph.addNode(app);
        }

        // Edges: prüfe jedes Paar A->B (A != B)
        for (int i = 0; i < apps.size(); i++) {
            FederatedAppDetailDTO from = apps.get(i);
            if (from == null || from.getAppConfig() == null) continue;

            for (int j = 0; j < apps.size(); j++) {
                if (i == j) continue;

                FederatedAppDetailDTO to = apps.get(j);
                if (to == null || to.getAppConfig() == null) continue;

                ToolGraphEdgeDTO edge = buildEdgeIfCompatible(from, to);
                if (edge != null) {
                    graph.getEdges().add(edge);
                }
            }
        }

        return graph;
    }

    private ToolGraphEdgeDTO buildEdgeIfCompatible(FederatedAppDetailDTO from, FederatedAppDetailDTO to) {
        List<ToolOutputConfigDTO> outputs = safeList(from.getAppConfig().getOutput());
        List<ToolInputConfigDTO> inputs = safeList(to.getAppConfig().getInput());

        if (outputs.isEmpty() || inputs.isEmpty()) return null;

        ToolGraphEdgeDTO edge = new ToolGraphEdgeDTO();
        edge.setFromAppId(from.getId());
        edge.setToAppId(to.getId());

        // Für jeden Input schauen wir, ob es einen passenden Output gibt
        for (ToolConfigDTO in : inputs) {
            if (in == null) continue;

            for (ToolConfigDTO out : outputs) {
                if (out == null) continue;

                if (isCompatible(out, in)) {
                    edge.getMatches().add(toMatch(out, in));
                }
            }

            // Optional: wenn ein Input required ist, aber gar keinen Match hat -> Edge trotzdem erlauben?
            // Ich empfehle: Edge bleibt erlaubt (weil partial wiring oft ok ist),
            // aber du kannst später "unfulfilled required inputs" im UI warnen.
        }

        if (edge.getMatches().isEmpty()) return null;

        edge.setScore(edge.getMatches().size());
        return edge;
    }

    private ToolPortMatchDTO toMatch(ToolConfigDTO out, ToolConfigDTO in) {
        ToolPortMatchDTO m = new ToolPortMatchDTO();
        m.setOutputVariable(bestVar(out));
        m.setInputVariable(bestVar(in));
        m.setOutputType(out.getType());
        m.setInputType(in.getType());

        if (Objects.equals(out.getType(), in.getType())) {
            m.setReason("same_type");
        } else {
            m.setReason("convertible");
        }
        return m;
    }

    private String bestVar(ToolConfigDTO cfg) {
        if (cfg.getVariableName() != null && !cfg.getVariableName().isBlank()) return cfg.getVariableName();
        return cfg.getName();
    }
    /**
     * Kernregel: OutputType muss InputType bedienen können.
     * Standard: gleich.
     * Optional: kleine Konvertierungsregeln (z.B. TSV <-> CSV, TABLE <-> CSV, etc.)
     */
    private boolean isCompatible(ToolConfigDTO out, ToolConfigDTO in) {
        if (out.getType() == null || in.getType() == null) return false;

        // 1) Gleichheit
        if (out.getType() == in.getType()) return true;

        // 2) Konvertierungen (bewusst klein halten!)
        return isConvertible(out, in);
    }

    private boolean isConvertible(ToolConfigDTO out, ToolConfigDTO in) {
        ToolConfigDataType o = out.getType();
        ToolConfigDataType t = in.getType();

        if (isOneOf(o, ToolConfigDataType.CSV, ToolConfigDataType.TSV)
                && isOneOf(t, ToolConfigDataType.CSV, ToolConfigDataType.TSV)) {
            return true;
        }

        // Weitere Regeln nur hinzufügen, wenn du sie wirklich in PoSyMed unterstützt.
        return false;
    }

    private boolean isOneOf(ToolConfigDataType x, ToolConfigDataType... xs) {
        for (ToolConfigDataType v : xs) if (v == x) return true;
        return false;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
