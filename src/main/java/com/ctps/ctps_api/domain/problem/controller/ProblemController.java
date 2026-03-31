package com.ctps.ctps_api.domain.problem.controller;

import com.ctps.ctps_api.domain.problem.dto.ProblemCreateRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResolveRequest;
import com.ctps.ctps_api.domain.problem.dto.ProblemMetadataResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemSolveHistoryResponse;
import com.ctps.ctps_api.domain.problem.dto.ProblemUpdateRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchRequest;
import com.ctps.ctps_api.domain.problem.dto.search.ProblemSearchResponse;
import com.ctps.ctps_api.domain.problem.service.ProblemMetadataService;
import com.ctps.ctps_api.domain.problem.service.ProblemService;
import com.ctps.ctps_api.domain.problem.service.ProblemSearchService;
import com.ctps.ctps_api.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final ProblemSearchService problemSearchService;
    private final ProblemMetadataService problemMetadataService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProblemResponse>>> getProblems() {
        return ResponseEntity.ok(ApiResponse.success("문제 목록 조회 성공", problemService.getProblems()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProblemResponse>> getProblem(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("문제 조회 성공", problemService.getProblem(id)));
    }

    @GetMapping("/{id}/solve-history")
    public ResponseEntity<ApiResponse<ProblemSolveHistoryResponse>> getSolveHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("문제 풀이 이력 조회 성공", problemService.getSolveHistory(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ProblemSearchResponse>> searchProblems(@Valid ProblemSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("문제 검색 성공", problemSearchService.search(request)));
    }

    @GetMapping("/metadata/resolve")
    public ResponseEntity<ApiResponse<ProblemMetadataResponse>> resolveProblemMetadata(@Valid ProblemMetadataResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("문제 메타데이터 조회 성공", problemMetadataService.resolve(request)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProblemResponse>> createProblem(@Valid @RequestBody ProblemCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("문제 등록 성공", problemService.createProblem(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProblemResponse>> updateProblem(
            @PathVariable Long id,
            @Valid @RequestBody ProblemUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("문제 수정 성공", problemService.updateProblem(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.ok(ApiResponse.success("문제 삭제 성공"));
    }
}
