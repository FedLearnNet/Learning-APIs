package de.unihamburg.daibetes.autoimport;

import io.quarkus.logging.Log;

import java.util.concurrent.atomic.AtomicInteger;

public class AutoImportCounter {

    private final String importName;
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    public AutoImportCounter(String importName) {
        this.importName = importName;
    }

    public void created() {
        created.incrementAndGet();
    }

    public void skipped() {
        skipped.incrementAndGet();
    }

    public void failed(String item, Throwable error) {
        failed.incrementAndGet();
        Log.warnf(error, "%s import: failed to import %s", importName, item);
    }

    public void log() {
        Log.infof("%s import finished: %d created, %d already existing, %d failed",
                importName, created.get(), skipped.get(), failed.get());
    }
}
