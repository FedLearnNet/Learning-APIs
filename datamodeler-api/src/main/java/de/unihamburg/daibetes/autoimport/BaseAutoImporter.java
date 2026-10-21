package de.unihamburg.daibetes.autoimport;

import bio.cosy.feddb.core.autoimport.AutoImportPathHandler;
import bio.cosy.feddb.core.autoimport.FLNetConfigPaths;
import bio.cosy.feddb.core.autoimport.OnParseError;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

public abstract class BaseAutoImporter<T> {

    @Inject
    AutoImportPathHandler autoImportPathHandler;

    protected abstract String getImportName();

    protected abstract FLNetConfigPaths getConfig();

    protected abstract Class<T> getType();

    protected abstract String describe(T item);

    protected abstract Uni<Void> importItem(T item, AutoImportContext context, AutoImportCounter counter);
    
    public Uni<Void> importAll(AutoImportContext context) {
        if (!getConfig().enabled()) {
            Log.infof("%s import has been disabled", getImportName());
            return Uni.createFrom().voidItem();
        }

        AutoImportCounter counter = new AutoImportCounter(getImportName());
        return load()
                .onItem().transformToMulti(items -> Multi.createFrom().iterable(items))
                .onItem().transformToUniAndConcatenate(item -> importItem(item, context, counter)
                        .onFailure().invoke(e -> counter.failed(describe(item), e))
                        .onFailure().recoverWithNull())
                .collect().last()
                .invoke(counter::log)
                .replaceWithVoid();
    }

    private Uni<List<T>> load() {
        // file and URL reads are blocking
        return Uni.createFrom().item(() -> autoImportPathHandler.loadAll(getImportName(), getConfig(), getType(), OnParseError.FAIL))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    protected <R> Uni<R> orNull(Uni<R> lookup) {
        return lookup.onFailure(NotFoundException.class).recoverWithNull();
    }
}
