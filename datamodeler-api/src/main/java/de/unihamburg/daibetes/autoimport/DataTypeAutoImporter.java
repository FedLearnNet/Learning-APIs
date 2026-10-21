package de.unihamburg.daibetes.autoimport;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.autoimport.FLNetConfigPaths;
import de.unihamburg.daibetes.api.datatype.DataTypeBO;
import de.unihamburg.daibetes.api.datatype.DataTypeDAO;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DataTypeAutoImporter extends BaseAutoImporter<DataTypeNodeDTO> {

    private static final String IMPORT_NAME = "Datatype";

    @Inject
    FLNetConfig flnet;

    @Inject
    DataTypeBO dataTypeBO;

    @Inject
    DataTypeDAO dataTypeDAO;

    @Override
    protected String getImportName() {
        return IMPORT_NAME;
    }

    @Override
    protected FLNetConfigPaths getConfig() {
        return flnet.datatypeImports();
    }

    @Override
    protected Class<DataTypeNodeDTO> getType() {
        return DataTypeNodeDTO.class;
    }

    @Override
    protected String describe(DataTypeNodeDTO item) {
        return item == null ? "<empty datatype>" : item.getName() + " (" + item.getId() + ")";
    }

    @Override
    protected Uni<Void> importItem(DataTypeNodeDTO item, AutoImportContext context, AutoImportCounter counter) {
        if (item == null || item.getId() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Datatype entry needs an ID"));
        }
        return orNull(dataTypeDAO.findById(item.getId())).chain(found -> {
            if (found != null) {
                counter.skipped();
                return Uni.createFrom().voidItem();
            }
            return dataTypeBO.create(item)
                    .invoke(ignored -> counter.created())
                    .replaceWithVoid();
        });
    }
}
