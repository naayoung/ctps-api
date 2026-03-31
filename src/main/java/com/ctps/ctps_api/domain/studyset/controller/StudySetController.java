package com.ctps.ctps_api.domain.studyset.controller;

import com.ctps.ctps_api.domain.studyset.dto.StudySetCreateRequest;
import com.ctps.ctps_api.domain.studyset.dto.StudySetResponse;
import com.ctps.ctps_api.domain.studyset.dto.StudySetUpdateRequest;
import com.ctps.ctps_api.domain.studyset.service.StudySetService;
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
@RequestMapping("/api/study-sets")
@RequiredArgsConstructor
public class StudySetController {

    private final StudySetService studySetService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudySetResponse>>> getStudySets() {
        return ResponseEntity.ok(ApiResponse.success("공부 세트 목록 조회 성공", studySetService.getStudySets()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudySetResponse>> getStudySet(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("공부 세트 조회 성공", studySetService.getStudySet(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudySetResponse>> createStudySet(@Valid @RequestBody StudySetCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("공부 세트 생성 성공", studySetService.createStudySet(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StudySetResponse>> updateStudySet(
            @PathVariable Long id,
            @Valid @RequestBody StudySetUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("공부 세트 수정 성공", studySetService.updateStudySet(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStudySet(@PathVariable Long id) {
        studySetService.deleteStudySet(id);
        return ResponseEntity.ok(ApiResponse.success("공부 세트 삭제 성공"));
    }
}
