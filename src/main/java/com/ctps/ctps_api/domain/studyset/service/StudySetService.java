package com.ctps.ctps_api.domain.studyset.service;

import com.ctps.ctps_api.domain.studyset.dto.StudySetCreateRequest;
import com.ctps.ctps_api.domain.studyset.dto.StudySetResponse;
import com.ctps.ctps_api.domain.studyset.dto.StudySetUpdateRequest;
import com.ctps.ctps_api.domain.studyset.entity.StudySet;
import com.ctps.ctps_api.domain.studyset.repository.StudySetRepository;
import com.ctps.ctps_api.global.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudySetService {

    private final StudySetRepository studySetRepository;

    public List<StudySetResponse> getStudySets() {
        return studySetRepository.findAll().stream().map(StudySetResponse::from).toList();
    }

    public StudySetResponse getStudySet(Long id) {
        return StudySetResponse.from(findById(id));
    }

    @Transactional
    public StudySetResponse createStudySet(StudySetCreateRequest request) {
        StudySet studySet = StudySet.builder()
                .name(request.getName())
                .problemIds(request.getProblemIds())
                .completedProblemIds(List.of())
                .createdAt(LocalDateTime.now())
                .build();
        return StudySetResponse.from(studySetRepository.save(studySet));
    }

    @Transactional
    public StudySetResponse updateStudySet(Long id, StudySetUpdateRequest request) {
        StudySet studySet = findById(id);
        studySet.patch(request.getName(), request.getProblemIds(), request.getCompletedProblemIds());
        return StudySetResponse.from(studySet);
    }

    @Transactional
    public void deleteStudySet(Long id) {
        StudySet studySet = findById(id);
        studySetRepository.delete(studySet);
    }

    private StudySet findById(Long id) {
        return studySetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("공부 세트를 찾을 수 없습니다. id=" + id));
    }
}
