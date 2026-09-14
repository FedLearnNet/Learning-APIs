package bio.cosy.feddb.core.api.workflow.base;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowInputDTO;
import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionDTO;
import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionStatics;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import io.quarkus.logging.Log;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BaseWorkflowEngine {

    public void customizeStart(StartWorkflowNodeDTO dto, Long experimentId, Long stepId) {
        dto.setWorkflowId(experimentId);
        dto.setWorkflowNodeId(stepId);
        dto.upsertEnv("APP_ID", String.valueOf(stepId));
    }

    public void customizeStart(StartWorkflowNodeDTO dto, Long experimentId, Integer orderId, Long stepId) {
        dto.setWorkflowId(experimentId);
        dto.setWorkflowNodeId(orderId.longValue());
        dto.upsertEnv("APP_ID", String.valueOf(stepId));
    }

    public StartWorkflowNodeDTO getStartDTO(WorkflowNodeDetailDTO node, String apiKey) {
        StartWorkflowNodeDTO dto = new StartWorkflowNodeDTO();
        dto.setWorkflowId(node.getWorkflowId());
        dto.setWorkflowNodeId(node.getId());

        ModelDetailDTO model = node.getModelDetail();
        FederatedAppDetailDTO detail = node.getAppDetail();
        if (model != null) {
            ModelSubDTO sub = model.getModelVersions().stream()
                    .flatMap(v -> v.getSubModels().stream())
                    .filter(s -> s.getId().equals(node.getModelSubId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Submodel with id " + node.getModelSubId() + " not found in model " + model.getId()));

            dto.setAppImage(sub.getImageName());
        } else if (detail != null) {
            dto.setAppImage(detail.getImageName());
        } else {
            throw new IllegalArgumentException("Node " + node.getId() + " has neither model nor app defined.");
        }
        dto.setEnvironments(List.of(
                "APP_ID=" + node.getId(),
                "APP_API_KEY=" + apiKey
        ));

        dto.setNeedsInternetAccess(node.needsInternetAccess());
        dto.setNeedsHostAccess(node.needsHostAccess());
        dto.setNeedsFederatedLearningAccess(node.needsFederatedLearningAccess());

        Log.infof("Starting container for app %d with image %s (internet=%s, host=%s, federatedLearning=%s)",
                node.getId(), dto.getAppImage(), dto.getNeedsInternetAccess(),
                dto.getNeedsHostAccess(), dto.getNeedsFederatedLearningAccess());

        return dto;
    }

    public Map<WorkflowInputDTO, Map<WorkflowConnectionDTO, WorkflowNodeDetailDTO>> getInputNodes(
            WorkflowDTO workflow
    ) {
        Map<WorkflowInputDTO, Map<WorkflowConnectionDTO, WorkflowNodeDetailDTO>> result =
                new LinkedHashMap<>();
        if (workflow == null) {
            return result;
        }
        List<WorkflowInputDTO> inputs = workflow.getInputs().stream().filter(Objects::nonNull).toList();
        List<WorkflowConnectionDTO> connections = workflow.getConnections().stream().filter(Objects::nonNull).toList();
        List<WorkflowNodeDetailDTO> nodes = workflow.getNodes().stream().filter(Objects::nonNull).toList();

        if (connections.isEmpty() || nodes.isEmpty() || inputs.isEmpty()) {
            return result;
        }

        for (WorkflowInputDTO input : workflow.getInputs()) {
            String inputNodeId = input.getNodeId();

            List<WorkflowConnectionDTO> inputConnections = connections.stream()
                    .filter(conn -> inputNodeId != null
                            && WorkflowConnectionStatics.getInputNodeVariableName(inputNodeId).equals(conn.getOutputId()))
                    .toList();

            Map<WorkflowConnectionDTO, WorkflowNodeDetailDTO> targets =
                    inputConnections.stream()
                            .map(c -> {
                                WorkflowNodeDetailDTO n = nodes.stream()
                                        .filter(node -> c.getInputNodeId().equals(node.getNodeId()))
                                        .findFirst()
                                        .orElse(null);
                                return Map.entry(c, n);
                            })
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (a, b) -> a,
                                    LinkedHashMap::new
                            ));

            result.put(input, targets);
        }

        return result;
    }

    public boolean hasInputNode(WorkflowDTO workflow, WorkflowNodeDetailDTO node) {
        if (workflow == null || node == null || node.getNodeId() == null) {
            return false;
        }

        if (workflow.getInputs() == null || workflow.getInputs().isEmpty()) {
            return false;
        }

        if (workflow.getConnections() == null || workflow.getConnections().isEmpty()) {
            return false;
        }

        // Collect all input nodeIds
        Set<String> inputNodeIds = workflow.getInputs().stream()
                .map(WorkflowInputDTO::getNodeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (inputNodeIds.isEmpty()) {
            return false;
        }

        String targetNodeId = node.getNodeId();

        List<String> connections = workflow.getConnections().stream()
                .filter(Objects::nonNull)
                .filter(c -> targetNodeId.equals(c.getInputNodeId()))
                .map(WorkflowConnectionDTO::getOutputId)
                .map(outputId -> outputId.replace(WorkflowConnectionStatics.INPUT_NODE_PREFIX.getValue(), ""))
                .toList();
        // Check if any connection links an input -> this node
        return connections.stream()
                .anyMatch(inputNodeIds::contains);
    }

    public void setExecutionOrder(WorkflowDTO workflow) {
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            return;
        }

        // --- Index nodes ---
        final List<WorkflowNodeDetailDTO> nodes = workflow.getNodes();
        final Map<String, WorkflowNodeDetailDTO> byId = nodes.stream()
                .filter(n -> n != null && n.getNodeId() != null)
                .collect(Collectors.toMap(WorkflowNodeDetailDTO::getNodeId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        // --- Build edges (dependencies) ---
        // Convention in this codebase (see nextNode(workflow, currentId)):
        // outputNodeId -> inputNodeId
        final Map<String, Set<String>> children = new HashMap<>();
        final Map<String, Integer> indegree = new HashMap<>();

        for (String id : byId.keySet()) {
            children.put(id, new LinkedHashSet<>());
            indegree.put(id, 0);
        }

        for (WorkflowConnectionDTO c : Optional.ofNullable(workflow.getConnections()).orElseGet(List::of)) {
            if (c == null) continue;
            final String from = c.getOutputNodeId();
            final String to = c.getInputNodeId();
            if (from == null || to == null) continue;
            if (!byId.containsKey(from) || !byId.containsKey(to)) continue;

            // avoid double edges
            if (children.get(from).add(to)) {
                indegree.put(to, indegree.getOrDefault(to, 0) + 1);
            }
        }

        // Sort each sibling list deterministically ("left to right" without explicit coordinates)
        final Map<String, List<String>> sortedChildren = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : children.entrySet()) {
            List<String> ch = new ArrayList<>(e.getValue());
            ch.sort(Comparator.nullsLast(String::compareTo));
            sortedChildren.put(e.getKey(), ch);
        }

        // --- Determine preferred start order (inputs left->right) ---
        final List<String> preferredStarts = Optional.ofNullable(workflow.getInputs()).orElseGet(List::of).stream()
                .map(WorkflowInputDTO::getNodeId)
                .filter(Objects::nonNull)
                .distinct()
                .filter(byId::containsKey)
                .toList();

        // Priority values: lower = earlier.
        final Map<String, Integer> startPriority = new HashMap<>();
        for (int i = 0; i < preferredStarts.size(); i++) {
            startPriority.put(preferredStarts.get(i), i);
        }

        // --- Kahn topo-sort with stable ordering ---
        // We maintain a queue of currently-free nodes. Ordering rules:
        //  1) nodes that are input-roots earlier in the inputs list first
        //  2) otherwise lexicographic nodeId for deterministic left->right
        final Comparator<String> availableCmp = (a, b) -> {
            int pa = startPriority.getOrDefault(a, Integer.MAX_VALUE);
            int pb = startPriority.getOrDefault(b, Integer.MAX_VALUE);
            if (pa != pb) return Integer.compare(pa, pb);
            // if both are not preferred starts, keep deterministic ordering
            return String.valueOf(a).compareTo(String.valueOf(b));
        };

        final PriorityQueue<String> available = new PriorityQueue<>(availableCmp);
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() != null && e.getValue() == 0) {
                available.add(e.getKey());
            }
        }

        final List<String> linearOrder = new ArrayList<>(byId.size());
        final Set<String> visited = new HashSet<>();

        // Helper to push children when they become available, preserving sibling order.
        final Function<String, Void> releaseChildren = (parent) -> {
            for (String childId : sortedChildren.getOrDefault(parent, List.of())) {
                indegree.put(childId, indegree.getOrDefault(childId, 0) - 1);
                if (indegree.get(childId) == 0) {
                    available.add(childId);
                }
            }
            return null;
        };

        // If there are explicit preferred starts, try to process them first when possible.
        // This respects dependencies because we only pull from 'available'.
        while (!available.isEmpty()) {
            // Pick the next available node by priority.
            final String nextId = available.poll();
            if (nextId == null || visited.contains(nextId)) continue;

            visited.add(nextId);
            linearOrder.add(nextId);
            releaseChildren.apply(nextId);
        }

        // Cycle detection / unresolved dependencies: fall back to deterministic order for remaining nodes.
        if (linearOrder.size() != byId.size()) {
            // Remaining nodes are part of a cycle or have missing edges; still assign an order deterministically.
            List<String> remaining = byId.keySet().stream().filter(id -> !visited.contains(id)).sorted().toList();
            linearOrder.addAll(remaining);
        }

        // --- Assign execution order ---
        // Use 0-based order; consumers can +1 if they want human-friendly ordering.
        for (int i = 0; i < linearOrder.size(); i++) {
            WorkflowNodeDetailDTO n = byId.get(linearOrder.get(i));
            if (n == null) continue;
            try {
                n.setExecutionOrder(i);
            } catch (Exception ignored) {
                // If the DTO doesn't expose a setter in this module/version, we still computed the order.
                Log.debugf("WorkflowNodeDetailDTO has no setExecutionOrder(int) in this module; computed order index=%s for nodeId=%s", i, n.getNodeId());
            }
        }
    }

    private WorkflowNodeDetailDTO findById(List<WorkflowNodeDetailDTO> nodes, String id) {
        for (WorkflowNodeDetailDTO n : nodes) {
            if (id.equals(n.getNodeId())) return n;
        }
        return null;
    }

    public WorkflowNodeDetailDTO findByNodeId(WorkflowDTO workflow, String nodeId) {
        if (nodeId == null) return null;
        return findById(workflow.getNodes(), nodeId);
    }

    public boolean isLastStep(WorkflowDTO workflow, String nodeId) {
        if (workflow == null || workflow.getNodes() == null) return false;
        if (nodeId == null) return false;
        WorkflowNodeDetailDTO node = findById(workflow.getNodes(), nodeId);
        return isLastStep(workflow, node);
    }

    public boolean isLastStep(WorkflowDTO workflow, WorkflowNodeDetailDTO node) {
        if (workflow == null || workflow.getNodes() == null) return false;
        if (node == null) return false;
        if (node.getExecutionOrder() == null) return false;
        int maxSteps = workflow.getNodes().size();
        return node.getExecutionOrder() >= maxSteps - 1;
    }

    public WorkflowNodeDetailDTO firstNode(WorkflowDTO workflow) {
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) return null;

        return workflow.getNodes().stream()
                .filter(Objects::nonNull)
                .filter(n -> n.getExecutionOrder() != null)
                .filter(n -> n.getExecutionOrder().equals(0))
                .findFirst().orElseGet(() -> null);
    }

    public WorkflowNodeDetailDTO nextNode(WorkflowDTO workflow, String currentNodeId) {
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) return null;
        if (currentNodeId == null) return firstNode(workflow);

        WorkflowNodeDetailDTO currentNode = findById(workflow.getNodes(), currentNodeId);
        if (currentNode == null || currentNode.getExecutionOrder() == null) return null;

        final int currentOrder = currentNode.getExecutionOrder() + 1;

        return workflow.getNodes().stream()
                .filter(Objects::nonNull)
                .filter(n -> n.getExecutionOrder() != null)
                .filter(n -> n.getExecutionOrder() == currentOrder)
                .findFirst().orElseGet(() -> null);

    }
}
