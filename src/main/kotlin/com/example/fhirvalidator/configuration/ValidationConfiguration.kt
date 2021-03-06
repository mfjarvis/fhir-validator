package com.example.fhirvalidator.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.validation.FhirValidator
import com.example.fhirvalidator.service.ImplementationGuideParser
import mu.KLogging
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.r4.model.StructureDefinition
import org.hl7.fhir.utilities.cache.NpmPackage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ValidationConfiguration(private val implementationGuideParser: ImplementationGuideParser) {
    companion object : KLogging()

    @Bean
    fun validator(fhirContext: FhirContext, validationSupport: IValidationSupport): FhirValidator {
        val validatorModule = FhirInstanceValidator(validationSupport)
        return fhirContext.newValidator().registerValidatorModule(validatorModule)
    }

    @Bean
    fun validationSupport(fhirContext: FhirContext, npmPackages: Array<NpmPackage>): CachingValidationSupport {
        val supportChain = ValidationSupportChain(
                DefaultProfileValidationSupport(fhirContext),
                InMemoryTerminologyServerValidationSupport(fhirContext),
                SnapshotGeneratingValidationSupport(fhirContext)
        )
        npmPackages.map(implementationGuideParser::createPrePopulatedValidationSupport).forEach(supportChain::addValidationSupport)
        generateSnapshots(supportChain)
        return CachingValidationSupport(supportChain)
    }

    fun generateSnapshots(supportChain: IValidationSupport) {
        supportChain.fetchAllStructureDefinitions<StructureDefinition>()
                .filter { shouldGenerateSnapshot(it) }
                .partition { it.baseDefinition.startsWith("http://hl7.org/fhir/") }
                .toList()
                .flatten()
                .forEach { supportChain.generateSnapshot(supportChain, it, it.url, "https://fhir.nhs.uk/R4", it.name) }
    }

    private fun shouldGenerateSnapshot(structureDefinition: StructureDefinition): Boolean {
        return !structureDefinition.hasSnapshot() && structureDefinition.derivation == StructureDefinition.TypeDerivationRule.CONSTRAINT
    }

//    fun generateSnapshots(supportChain: IValidationSupport) {
//        val structureDefinitionsToProcess = supportChain.fetchAllStructureDefinitions<StructureDefinition>()
//                .filter { shouldGenerateSnapshot(it) }
//                .toMutableList()
//
//        while (structureDefinitionsToProcess.isNotEmpty()) {
//            val structureDefinition = structureDefinitionsToProcess.removeAt(0)
//            val baseStructureDefinition = supportChain.fetchStructureDefinition(structureDefinition.baseDefinition) as StructureDefinition
//            if (baseStructureDefinition.hasSnapshot()) {
//                supportChain.generateSnapshot(supportChain, structureDefinition, structureDefinition.url, "https://fhir.nhs.uk/R4", structureDefinition.name)
//            } else {
//                //We haven't processed the base structure definition yet, add back to the queue
//                //TODO - detect circular references
//                structureDefinitionsToProcess.add(structureDefinition)
//            }
//        }
//    }

//    fun generateSnapshots(supportChain: IValidationSupport) {
//        supportChain.fetchAllStructureDefinitions<StructureDefinition>()
//                .filter { it.derivation == StructureDefinition.TypeDerivationRule.CONSTRAINT }
//                .forEach { ensureSnapshotGenerated(supportChain, it) }
//    }
//
//    private fun ensureSnapshotGenerated(supportChain: IValidationSupport, structureDefinition: StructureDefinition) {
//        structureDefinition.baseDefinition?.let {
//            val baseStructureDefinition = supportChain.fetchStructureDefinition(it) as StructureDefinition
//            ensureSnapshotGenerated(supportChain, baseStructureDefinition)
//        }
//
//        if (!structureDefinition.hasSnapshot()) {
//            supportChain.generateSnapshot(supportChain, structureDefinition, structureDefinition.url, "https://fhir.nhs.uk/R4", structureDefinition.name)
//        }
//    }
}