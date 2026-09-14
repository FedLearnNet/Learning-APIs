package de.unihamburg.daibetes.api.umls.importer;


import bio.cosy.feddb.core.api.datamodler.ontology.OntologyEdgeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@ApplicationScoped
public class UmlsParser {

    @ConfigProperty(name = "umls.language.filter", defaultValue = "eng")
    List<String> languageFilter;

    @ConfigProperty(name = "umls.sab.filter", defaultValue = "SNOMEDCT_US,RXNORM,LNC,ICD10,NCBI")
    List<String> sabFilter;

    @ConfigProperty(name = "umls.import.allow-self-loops", defaultValue = "false")
    boolean allowSelfLoops;

    @ConfigProperty(name = "umls.import.folder-path")
    Optional<String> importFolderPath;

    public Multi<List<OntologyEdgeDTO>> parseMrrelToEdgesBatched(
            String path,
            int batchSize,
            long maxElements
    ) {
        if (path == null || path.isEmpty()) {
            throw new BadRequestException("Path to Mrrel file cannot be null");
        }

        String resolvedPath = importFolderPath.map(s -> Paths.get(s, path).toString()).orElseGet(() -> Optional.ofNullable(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource(path)).orElseThrow(() -> new BadRequestException("UMLS Mrrel file not found: " + path))
                .getPath());

        Multi<List<OntologyEdgeDTO>> multi = Multi.createFrom().resource(
                        () -> {
                            try {
                                return Files.lines(Paths.get(resolvedPath));
                            } catch (IOException e) {
                                Log.errorf("Failed to read lines for path: %s", resolvedPath);
                                throw new RuntimeException(e);
                            }
                        },

                        (Stream<String> stream) -> {
                            Iterable<String> iterable = stream::iterator;

                            Multi<OntologyEdgeDTO> edgeStream =
                                    Multi.createFrom().iterable(iterable)
                                            .onItem().transformToMulti(line -> {
                                                OntologyEdgeDTO dto = mrrelLineToEdge(line);
                                                if (dto == null) {
                                                    return Multi.createFrom().empty();
                                                }
                                                return Multi.createFrom().item(dto);
                                            })
                                            .merge();

                            if (maxElements > 0) {
                                edgeStream = edgeStream.select().first(maxElements);
                            }

                            return edgeStream
                                    .group().intoLists().of(batchSize);
                        }
                ).withFinalizer(stream -> {
                    stream.close();
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onFailure().transform(e -> {
                            Log.errorf("Error processing UMLS file: %s", resolvedPath, e);
                            return e;
                        }
                );

        return multi;
    }

    public Multi<List<OntologyNodeDTO>> parseMrconsoToNodesBatched(
            String path,
            int batchSize,
            long maxElements
    ) {
        if (path == null || path.isEmpty()) {
            throw new BadRequestException("Path to MRCONSO file cannot be null");
        }
        String resolvedPath = importFolderPath.map(s -> Paths.get(s, path).toString()).orElseGet(() -> Optional.ofNullable(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource(path)).orElseThrow(() -> new BadRequestException("UMLS MRCONSO file not found: " + path))
                .getPath());

        Multi<List<OntologyNodeDTO>> multi = Multi.createFrom().resource(
                        () -> {
                            try {
                                return Files.lines(Paths.get(resolvedPath));
                            } catch (IOException e) {
                                Log.errorf("Failed to read lines for path: %s", resolvedPath);
                                throw new RuntimeException(e);
                            }
                        },

                        (Stream<String> stream) -> {
                            Iterable<String> iterable = stream::iterator;

                            Multi<OntologyNodeDTO> nodeStream =
                                    Multi.createFrom().iterable(iterable)
                                            .onItem().transformToMulti(line -> {
                                                OntologyNodeDTO dto = mrconsoLineToNode(line);
                                                if (dto == null) {
                                                    return Multi.createFrom().empty();
                                                }
                                                return Multi.createFrom().item(dto);
                                            })
                                            .merge();

                            if (maxElements > 0) {
                                nodeStream = nodeStream.select().first(maxElements);
                            }

                            return nodeStream
                                    .group().intoLists().of(batchSize);
                        }
                ).withFinalizer(stream -> {
                    stream.close();
                })
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onFailure().transform(e -> {
                            Log.errorf("Error processing UMLS file: %s", resolvedPath, e);
                            return e;
                        }
                );

        return multi;
    }

    private List<OntologyEdgeDTO> parseMrrelToEdges(String path, long from, long to) {
        String resolvedPath = Thread.currentThread().getContextClassLoader().getResource(path).getPath();
        try (Stream<String> stream = Files.lines(Paths.get(resolvedPath))) {
            return stream
                    .map(this::mrrelLineToEdge)
                    .skip(from)
                    .limit(to - from)
                    .toList();
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Error reading UMLS file: " + path, e));
        }
    }

    private List<OntologyNodeDTO> parseMrconsoToNodes(String path, long from, long to) {
        String resolvedPath = Thread.currentThread().getContextClassLoader().getResource(path).getPath();
        try (Stream<String> stream = Files.lines(Paths.get(resolvedPath))) {
            return stream
                    .map(this::mrconsoLineToNode)
                    .filter(Objects::nonNull)
                    .skip(from)
                    .limit(to - from)
                    .toList();
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Error reading UMLS file: " + path, e));
        }
    }


    private OntologyNodeDTO mrconsoLineToNode(String line) {
        // RRF is pipe-separated, keep empty columns
        String[] c = line.split("\\|", -1);

        // Basic sanity check – if something is off, log and skip by throwing
        if (c.length < 15) {
            Log.warnf("MRCONSO line has too few columns (%d): %s", c.length, line);
            throw new IllegalArgumentException("Invalid MRCONSO line, not enough columns");
        }

        String cui = c[0];   // CUI
        String lat = c[1];   // LAT
        String aui = c[7];   // AUI
        String sab = c[11];  // SAB
        String code = c[13];  // CODE
        String str = c[14];  // STR

        if (!languageFilter.isEmpty() && !languageFilter.contains(lat.toLowerCase())) {
            return null;
        }
        if (!sabFilter.isEmpty() && !sabFilter.contains(sab)) {
            return null;
        }
        OntologyNodeDTO dto = new OntologyNodeDTO();
        dto.setCui(cui);
        dto.addAui(aui);
        dto.addName(str);
        dto.setLat(lat);
        dto.addCode(code);
        dto.addSab(sab);
        //ensure reproducible UUID for the same CUI,
        // so that we can link edges to nodes without having to do a separate lookup
        dto.setId(UUID.nameUUIDFromBytes(cui.getBytes(StandardCharsets.UTF_8)));

        return dto;
    }

    private OntologyEdgeDTO mrrelLineToEdge(String line) {
        String[] c = line.split("\\|", -1);

        if (c.length < 11) {
            Log.warnf("MRREL line has too few columns (%d): %s", c.length, line);
            throw new IllegalArgumentException("Invalid MRREL line, not enough columns");
        }

        String cui1 = c[0];   // CUI1
        String cui2 = c[4];   // CUI2
        String rel = c[3];   // REL
        String rela = c[7];   // RELA
        if (rela == null || rela.isEmpty()) {
            rela = "UNKNOWN";
        }
        String sab = c[10];  // SAB
        if (!sabFilter.isEmpty() && !sabFilter.contains(sab)) {
            return null;
        }
        if (!allowSelfLoops && cui1.equals(cui2)) {
            return null;
        }

        OntologyEdgeDTO dto = new OntologyEdgeDTO();
        dto.setSourceId(cui1);
        dto.setTargetId(cui2);
        dto.setRel(rel);
        dto.setRela(rela);
        dto.setSab(sab);

        return dto;
    }
}
