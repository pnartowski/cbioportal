package org.cbioportal.legacy.service.impl;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.cbioportal.legacy.model.GenericAssayAdditionalProperty;
import org.cbioportal.legacy.model.GenericAssayData;
import org.cbioportal.legacy.model.GenericAssayMolecularAlteration;
import org.cbioportal.legacy.model.MolecularProfile;
import org.cbioportal.legacy.model.MolecularProfile.MolecularAlterationType;
import org.cbioportal.legacy.model.MolecularProfileSamples;
import org.cbioportal.legacy.model.Sample;
import org.cbioportal.legacy.model.meta.GenericAssayMeta;
import org.cbioportal.legacy.persistence.GenericAssayRepository;
import org.cbioportal.legacy.persistence.MolecularDataRepository;
import org.cbioportal.legacy.persistence.SampleListRepository;
import org.cbioportal.legacy.service.GenericAssayService;
import org.cbioportal.legacy.service.MolecularProfileService;
import org.cbioportal.legacy.service.SampleService;
import org.cbioportal.legacy.service.exception.MolecularProfileNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class GenericAssayServiceImpl implements GenericAssayService {

    @Autowired
    private GenericAssayRepository genericAssayRepository;

    @Autowired
    private MolecularDataRepository molecularDataRepository;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private MolecularProfileService molecularProfileService;

    @Autowired
    private SampleListRepository sampleListRepository;

    @Override
    public List<GenericAssayMeta> getGenericAssayMetaByStableIdsAndMolecularIds(List<String> stableIds, List<String> molecularProfileIds, String projection) {
        Set<String> allStableIds = new HashSet<String>();
        // extract genericAssayStableIds from the GENERIC_ASSAY profiles
        if (molecularProfileIds != null) {
            List<String> distinctMolecularProfileIds = molecularProfileIds.stream().distinct().sorted().collect(Collectors.toList());
            if (distinctMolecularProfileIds.size() > 0) {
                // fetch one profile at a time to improve cache performace for multiple profiles query
                for (String distinctMolecularProfileId : distinctMolecularProfileIds) {
                    allStableIds.addAll(genericAssayRepository.getGenericAssayStableIdsByMolecularIds(Arrays.asList(distinctMolecularProfileId)));
                }
            }
            // if stableIds and molecularProfileIds both exist, find the common
            if (stableIds != null) {
                Map<String, String> allStableIdMap = allStableIds
                        .stream()
                        .collect(Collectors.toMap(stableId -> stableId, stableId -> stableId));

                allStableIds = stableIds
                        .stream()
                        .filter(stableId -> allStableIdMap.containsKey(stableId))
                        .collect(Collectors.toSet());
            }
        } else {
            // add all stableIds since molecularProfileIds is null
            if (stableIds != null) {
                allStableIds.addAll(stableIds);
            }
        }
        List<String> distinctStableIds = new ArrayList<String>(allStableIds);
        List<GenericAssayMeta> metaResults = new ArrayList<GenericAssayMeta>();
        //TODO: move below logic to sql query
        if (distinctStableIds.size() > 0) {
            List<GenericAssayMeta> metaData = genericAssayRepository.getGenericAssayMeta(distinctStableIds);
            // just return stable_id if projection is ID
            if (projection.equals("ID")) {
                for (GenericAssayMeta meta : metaData) {
                    metaResults.add(new GenericAssayMeta(meta.getStableId()));
                }
            } else {
                Map<String, List<GenericAssayAdditionalProperty>> additionalPropertiesGroupedByStableId =
                    genericAssayRepository.getGenericAssayAdditionalproperties(distinctStableIds).stream()
                        .collect(Collectors.groupingBy(GenericAssayAdditionalProperty::getStableId));
                for (GenericAssayMeta meta : metaData) {
                    String stableId = meta.getStableId();
                    HashMap<String, String> map = new HashMap<>();
                    if (additionalPropertiesGroupedByStableId.containsKey(stableId)) {
                       for (GenericAssayAdditionalProperty additionalProperty : additionalPropertiesGroupedByStableId.get(stableId)) {
                           map.put(additionalProperty.getName(), additionalProperty.getValue());
                       }
                    }
                    meta.setGenericEntityMetaProperties(map);
                    metaResults.add(meta);
                }
            }
        }
        return metaResults;
    }

    @Override
    public List<GenericAssayData> fetchGenericAssayData(List<String> molecularProfileIds, 
    List<String> sampleIds, List<String> genericAssayStableIds, String projection) throws MolecularProfileNotFoundException {
        List<GenericAssayData> result = new ArrayList<>();

        SortedSet<String> distinctMolecularProfileIds = new TreeSet<>(molecularProfileIds);

        Map<String, MolecularProfileSamples> commaSeparatedSampleIdsOfMolecularProfilesMap = molecularDataRepository
                .commaSeparatedSampleIdsOfMolecularProfilesMap(distinctMolecularProfileIds);

        Map<String, Map<Integer, Integer>> internalSampleIdsMap = new HashMap<>();
        List<Integer> allInternalSampleIds = new ArrayList<>();

        for (String molecularProfileId : distinctMolecularProfileIds) {
            List<Integer> internalSampleIds = Arrays
                    .stream(commaSeparatedSampleIdsOfMolecularProfilesMap.get(molecularProfileId).getSplitSampleIds())
                    .mapToInt(Integer::parseInt).boxed().collect(Collectors.toList());
            HashMap<Integer, Integer> molecularProfileSampleMap = new HashMap<Integer, Integer>();
            for (int lc = 0; lc < internalSampleIds.size(); lc++) {
                molecularProfileSampleMap.put(internalSampleIds.get(lc), lc);
            }
            internalSampleIdsMap.put(molecularProfileId, molecularProfileSampleMap);
            allInternalSampleIds.addAll(internalSampleIds);
        }
    
        List<MolecularProfile> molecularProfiles = new ArrayList<>();
        List<MolecularProfile> distinctMolecularProfiles = molecularProfileService.getMolecularProfiles(
            distinctMolecularProfileIds, "SUMMARY");
        Map<String, MolecularProfile> molecularProfileMapById = distinctMolecularProfiles.stream().collect(
            Collectors.toMap(MolecularProfile::getStableId, Function.identity()));
        Map<String, List<MolecularProfile>> molecularProfileMapByStudyId = distinctMolecularProfiles.stream().collect(
            Collectors.groupingBy(MolecularProfile::getCancerStudyIdentifier));
        List<Sample> samples;
        if (sampleIds == null) {
            samples = sampleService.getSamplesByInternalIds(allInternalSampleIds);
            for (String molecularProfileId : distinctMolecularProfileIds) {
                internalSampleIdsMap.get(molecularProfileId).keySet().forEach(s -> molecularProfiles.add(molecularProfileMapById
                    .get(molecularProfileId)));
            }
        } else {
            for (String molecularProfileId : molecularProfileIds) {
                molecularProfiles.add(molecularProfileMapById.get(molecularProfileId));
            }
            List<String> studyIds = molecularProfiles.stream().map(MolecularProfile::getCancerStudyIdentifier)
                .collect(Collectors.toList());
            samples = sampleService.fetchSamples(studyIds, sampleIds, "ID");
        }
    
        List<GenericAssayMolecularAlteration> molecularAlterations = new ArrayList<>();
        for (String distinctMolecularProfileId : distinctMolecularProfileIds) {
            molecularAlterations.addAll(molecularDataRepository.getGenericAssayMolecularAlterations(
                distinctMolecularProfileId, genericAssayStableIds, projection));
        }
        Map<String, List<GenericAssayMolecularAlteration>> molecularAlterationsMap = molecularAlterations.stream().collect(
            Collectors.groupingBy(GenericAssayMolecularAlteration::getMolecularProfileId));
        
        for (Sample sample : samples) {
            for (MolecularProfile molecularProfile : molecularProfileMapByStudyId.get(sample.getCancerStudyIdentifier())) {
                String molecularProfileId = molecularProfile.getStableId();
                Integer indexOfSampleId = internalSampleIdsMap.get(molecularProfileId).get(sample.getInternalId());
                if (indexOfSampleId != null && molecularAlterationsMap.containsKey(molecularProfileId)) {
                    for (GenericAssayMolecularAlteration molecularAlteration : molecularAlterationsMap.get(molecularProfileId)) {
                        GenericAssayData molecularData = new GenericAssayData();
                        molecularData.setMolecularProfileId(molecularProfileId);
                        molecularData.setSampleId(sample.getStableId());
                        molecularData.setPatientId(sample.getPatientStableId());
                        molecularData.setStudyId(sample.getCancerStudyIdentifier());
                        molecularData.setGenericAssayStableId(molecularAlteration.getGenericAssayStableId());
                        molecularData.setValue(molecularAlteration.getSplitValues()[indexOfSampleId]);
                        if (molecularProfile.getPatientLevel() != null) {
                            molecularData.setPatientLevel(molecularProfile.getPatientLevel());
                        }
                        result.add(molecularData);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<GenericAssayData> getGenericAssayData(String molecularProfileId, String sampleListId,
                                                    List<String> genericAssayStableIds, String projection)
        throws MolecularProfileNotFoundException {
        
        validateMolecularProfile(molecularProfileId);
        List<String> sampleIds = sampleListRepository.getAllSampleIdsInSampleList(sampleListId);
        if (sampleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return fetchGenericAssayData(Arrays.asList(molecularProfileId), sampleIds, genericAssayStableIds, projection);
    }

    @Override
    public List<GenericAssayData> fetchGenericAssayData(String molecularProfileId, List<String> sampleIds,
            List<String> genericAssayStableIds, String projection) throws MolecularProfileNotFoundException {

        validateMolecularProfile(molecularProfileId);
        return fetchGenericAssayData(Arrays.asList(molecularProfileId), sampleIds, genericAssayStableIds, projection);
    }

    private void validateMolecularProfile(String molecularProfileId) throws MolecularProfileNotFoundException {

        MolecularProfile molecularProfile = molecularProfileService.getMolecularProfile(molecularProfileId);

        if (!molecularProfile.getMolecularAlterationType().equals(MolecularAlterationType.GENERIC_ASSAY)) {

            throw new MolecularProfileNotFoundException(molecularProfileId);
        }
    }
}