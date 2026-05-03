package ru.normacontrol.application.mapper;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import ru.normacontrol.application.dto.response.CheckResultResponse;
import ru.normacontrol.application.dto.response.ViolationResponse;
import ru.normacontrol.domain.entity.CheckResult;
import ru.normacontrol.domain.entity.Violation;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-04T00:04:27+0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class CheckResultMapperImpl implements CheckResultMapper {

    @Override
    public CheckResultResponse toResponse(CheckResult checkResult) {
        if ( checkResult == null ) {
            return null;
        }

        CheckResultResponse.CheckResultResponseBuilder checkResultResponse = CheckResultResponse.builder();

        checkResultResponse.id( checkResult.getId() );
        checkResultResponse.documentId( checkResult.getDocumentId() );
        checkResultResponse.passed( checkResult.isPassed() );
        checkResultResponse.totalViolations( checkResult.getTotalViolations() );
        checkResultResponse.summary( checkResult.getSummary() );
        checkResultResponse.checkedAt( checkResult.getCheckedAt() );
        checkResultResponse.checkedBy( checkResult.getCheckedBy() );
        checkResultResponse.complianceScore( checkResult.getComplianceScore() );
        checkResultResponse.ruleSetName( checkResult.getRuleSetName() );
        checkResultResponse.ruleSetVersion( checkResult.getRuleSetVersion() );
        checkResultResponse.processingTimeMs( checkResult.getProcessingTimeMs() );
        checkResultResponse.reportStoragePath( checkResult.getReportStoragePath() );
        checkResultResponse.uniquenessPercent( checkResult.getUniquenessPercent() );
        checkResultResponse.plagiarismResult( checkResult.getPlagiarismResult() );
        checkResultResponse.violations( violationListToViolationResponseList( checkResult.getViolations() ) );

        return checkResultResponse.build();
    }

    @Override
    public ViolationResponse violationToResponse(Violation violation) {
        if ( violation == null ) {
            return null;
        }

        ViolationResponse.ViolationResponseBuilder violationResponse = ViolationResponse.builder();

        violationResponse.id( violation.getId() );
        violationResponse.ruleCode( violation.getRuleCode() );
        violationResponse.description( violation.getDescription() );
        violationResponse.pageNumber( violation.getPageNumber() );
        violationResponse.lineNumber( violation.getLineNumber() );
        violationResponse.suggestion( violation.getSuggestion() );
        violationResponse.aiSuggestion( violation.getAiSuggestion() );
        violationResponse.ruleReference( violation.getRuleReference() );

        violationResponse.severity( violation.getSeverity().name() );

        return violationResponse.build();
    }

    protected List<ViolationResponse> violationListToViolationResponseList(List<Violation> list) {
        if ( list == null ) {
            return null;
        }

        List<ViolationResponse> list1 = new ArrayList<ViolationResponse>( list.size() );
        for ( Violation violation : list ) {
            list1.add( violationToResponse( violation ) );
        }

        return list1;
    }
}
