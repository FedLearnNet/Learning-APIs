package bio.cosy.feddb.core.api.store.graph;

import java.util.*;
import java.util.stream.Collectors;

public class ToolGraphPathService {

    public List<ToolGraphPathDTO> findShortestPaths(ToolGraphDTO graph, Long from, Long to, int many) {
        if (graph == null || from == null || to == null) return List.of();
        if (many <= 0) many = 1;

        // adjacency: from -> list of edges
        Map<Long, List<ToolGraphEdgeDTO>> adj = buildAdj(graph);

        // 1) first shortest path (BFS)
        List<ToolGraphEdgeDTO> first = shortestPathBfs(adj, from, to, Set.of(), Set.of());
        if (first.isEmpty()) return List.of();

        if (many == 1) return List.of(toPath(from, to, first));

        // 2) Yen's algorithm for k shortest loopless paths
        List<List<ToolGraphEdgeDTO>> A = new ArrayList<>(); // found paths
        A.add(first);

        // candidate paths with ordering by hops (then tie-breaker)
        PriorityQueue<PathCandidate> B = new PriorityQueue<>();

        for (int k = 1; k < many; k++) {
            List<ToolGraphEdgeDTO> prevPath = A.get(k - 1);

            // build node sequence for prevPath
            List<Long> prevNodes = edgesToNodes(from, prevPath);

            for (int i = 0; i < prevNodes.size() - 1; i++) {
                Long spurNode = prevNodes.get(i);

                // root path edges = edges from start to spurNode
                List<ToolGraphEdgeDTO> rootEdges = prefixEdges(from, prevPath, i);

                // nodes in root path (to avoid cycles)
                Set<Long> bannedNodes = new HashSet<>();
                // ban all nodes of root path except spurNode (loopless constraint)
                for (int x = 0; x < i; x++) bannedNodes.add(prevNodes.get(x));

                // remove edges that would recreate previously found paths with same root
                Set<EdgeKey> bannedEdges = new HashSet<>();
                for (List<ToolGraphEdgeDTO> p : A) {
                    List<Long> pNodes = edgesToNodes(from, p);
                    if (pNodes.size() > i && samePrefix(pNodes, prevNodes, i)) {
                        // ban the next edge out of spur node in that path
                        ToolGraphEdgeDTO edgeToBan = p.get(i);
                        bannedEdges.add(new EdgeKey(edgeToBan.getFromAppId(), edgeToBan.getToAppId()));
                    }
                }

                // spur path from spurNode to target with banned nodes/edges
                List<ToolGraphEdgeDTO> spurEdges = shortestPathBfs(adj, spurNode, to, bannedNodes, bannedEdges);
                if (spurEdges.isEmpty()) continue;

                // total path = rootEdges + spurEdges
                List<ToolGraphEdgeDTO> total = new ArrayList<>(rootEdges);
                total.addAll(spurEdges);

                B.add(new PathCandidate(total));
            }

            if (B.isEmpty()) break;

            // next shortest candidate
            List<ToolGraphEdgeDTO> next = B.poll().edges;

            // dedupe (important when multiple spurs create same path)
            if (!containsPath(A, next)) {
                A.add(next);
            } else {
                // if duplicated, keep polling until unique or empty
                while (!B.isEmpty() && containsPath(A, next)) {
                    next = B.poll().edges;
                }
                if (!containsPath(A, next)) A.add(next);
                else break;
            }
        }

        return A.stream()
                .map(p -> toPath(from, to, p))
                .collect(Collectors.toList());
    }

    /* ================= helpers ================= */

    private Map<Long, List<ToolGraphEdgeDTO>> buildAdj(ToolGraphDTO graph) {
        Map<Long, List<ToolGraphEdgeDTO>> adj = new HashMap<>();
        for (ToolGraphEdgeDTO e : safe(graph.getEdges())) {
            if (e == null || e.getFromAppId() == null || e.getToAppId() == null) continue;
            adj.computeIfAbsent(e.getFromAppId(), __ -> new ArrayList<>()).add(e);
        }
        return adj;
    }

    /**
     * BFS shortest path (edges). Supports banning nodes + banning specific directed edges.
     */
    private List<ToolGraphEdgeDTO> shortestPathBfs(
            Map<Long, List<ToolGraphEdgeDTO>> adj,
            Long start,
            Long goal,
            Set<Long> bannedNodes,
            Set<EdgeKey> bannedEdges
    ) {
        if (Objects.equals(start, goal)) return List.of();
        if (bannedNodes.contains(start)) return List.of();

        ArrayDeque<Long> q = new ArrayDeque<>();
        Map<Long, ToolGraphEdgeDTO> parentEdge = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        visited.add(start);
        q.add(start);

        while (!q.isEmpty()) {
            Long u = q.poll();
            for (ToolGraphEdgeDTO e : safe(adj.get(u))) {
                Long v = e.getToAppId();
                if (v == null) continue;
                if (bannedNodes.contains(v)) continue;
                if (bannedEdges.contains(new EdgeKey(e.getFromAppId(), e.getToAppId()))) continue;
                if (visited.contains(v)) continue;

                visited.add(v);
                parentEdge.put(v, e);

                if (Objects.equals(v, goal)) {
                    return reconstructEdges(start, goal, parentEdge);
                }
                q.add(v);
            }
        }
        return List.of();
    }

    private List<ToolGraphEdgeDTO> reconstructEdges(Long start, Long goal, Map<Long, ToolGraphEdgeDTO> parentEdge) {
        List<ToolGraphEdgeDTO> edges = new ArrayList<>();
        Long cur = goal;
        while (!Objects.equals(cur, start)) {
            ToolGraphEdgeDTO e = parentEdge.get(cur);
            if (e == null) return List.of(); // safety
            edges.add(e);
            cur = e.getFromAppId();
        }
        Collections.reverse(edges);
        return edges;
    }

    private ToolGraphPathDTO toPath(Long from, Long to, List<ToolGraphEdgeDTO> edges) {
        ToolGraphPathDTO p = new ToolGraphPathDTO();
        p.setFromAppId(from);
        p.setToAppId(to);
        p.setEdges(new ArrayList<>(edges));
        p.setHops(edges.size());
        return p;
    }

    private List<Long> edgesToNodes(Long start, List<ToolGraphEdgeDTO> edges) {
        List<Long> nodes = new ArrayList<>();
        nodes.add(start);
        Long cur = start;
        for (ToolGraphEdgeDTO e : edges) {
            // assume consistent chain
            cur = e.getToAppId();
            nodes.add(cur);
        }
        return nodes;
    }

    /**
     * prefix edges length = i (edges index 0..i-1), where i is spur node index in node list.
     */
    private List<ToolGraphEdgeDTO> prefixEdges(Long start, List<ToolGraphEdgeDTO> edges, int spurNodeIndex) {
        // spurNodeIndex = index in node list, so prefix edges count = spurNodeIndex
        if (spurNodeIndex <= 0) return List.of();
        return new ArrayList<>(edges.subList(0, spurNodeIndex));
    }

    private boolean samePrefix(List<Long> a, List<Long> b, int uptoIndexInclusiveSpur) {
        // compare nodes[0..uptoIndexInclusiveSpur]
        if (a.size() <= uptoIndexInclusiveSpur || b.size() <= uptoIndexInclusiveSpur) return false;
        for (int i = 0; i <= uptoIndexInclusiveSpur; i++) {
            if (!Objects.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private boolean containsPath(List<List<ToolGraphEdgeDTO>> paths, List<ToolGraphEdgeDTO> candidate) {
        String sig = signature(candidate);
        for (List<ToolGraphEdgeDTO> p : paths) {
            if (signature(p).equals(sig)) return true;
        }
        return false;
    }

    private String signature(List<ToolGraphEdgeDTO> edges) {
        return edges.stream()
                .map(e -> e.getFromAppId() + "->" + e.getToAppId())
                .collect(Collectors.joining("|"));
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static class EdgeKey {
        final Long from;
        final Long to;

        EdgeKey(Long from, Long to) {
            this.from = from;
            this.to = to;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgeKey k)) return false;
            return Objects.equals(from, k.from) && Objects.equals(to, k.to);
        }
        @Override public int hashCode() {
            return Objects.hash(from, to);
        }
    }

    private static class PathCandidate implements Comparable<PathCandidate> {
        final List<ToolGraphEdgeDTO> edges;
        final int hops;
        final String sig;

        PathCandidate(List<ToolGraphEdgeDTO> edges) {
            this.edges = edges;
            this.hops = edges.size();
            this.sig = edges.stream()
                    .map(e -> e.getFromAppId() + "->" + e.getToAppId())
                    .collect(Collectors.joining("|"));
        }

        @Override public int compareTo(PathCandidate o) {
            int c = Integer.compare(this.hops, o.hops);
            if (c != 0) return c;
            return this.sig.compareTo(o.sig); // stable tie-break
        }
    }
}
