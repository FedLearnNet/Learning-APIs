package de.unihamburg.daibetes.api.feature;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FeatureAvailableServiceImpl implements FeatureAvailableService {

    @Inject
    FeatureAvailableBO featureAvailableBO;

    @Override
    public FeatureAvailableDTO get() {
        return featureAvailableBO.get();
    }
}
