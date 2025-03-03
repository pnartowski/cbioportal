package org.cbioportal.legacy.persistence;

import org.cbioportal.legacy.model.TypeOfCancer;
import org.cbioportal.legacy.model.meta.BaseMeta;

import org.springframework.cache.annotation.Cacheable;

import java.util.List;

public interface CancerTypeRepository {

    @Cacheable(cacheResolver = "generalRepositoryCacheResolver", condition = "@cacheEnabledConfig.getEnabled()")
    List<TypeOfCancer> getAllCancerTypes(String projection, Integer pageSize, Integer pageNumber, String sortBy,
                                         String direction);
    @Cacheable(cacheResolver = "generalRepositoryCacheResolver", condition = "@cacheEnabledConfig.getEnabled()")
    BaseMeta getMetaCancerTypes();

    @Cacheable(cacheResolver = "generalRepositoryCacheResolver", condition = "@cacheEnabledConfig.getEnabled()")
    TypeOfCancer getCancerType(String cancerTypeId);
}
