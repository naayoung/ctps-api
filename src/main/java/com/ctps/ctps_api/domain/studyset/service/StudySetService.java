package com.ctps.ctps_api.domain.studyset.service;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import com.ctps.ctps_api.domain.studyset.dto.StudySetCreateRequest;
import com.ctps.ctps_api.domain.studyset.dto.StudySetResponse;
import com.ctps.ctps_api.domain.studyset.dto.StudySetUpdateRequest;
import com.ctps.ctps_api.domain.studyset.entity.StudySet;
import com.ctps.ctps_api.domain.studyset.repository.StudySetRepository;
import com.ctps.ctps_api.global.exception.NotFoundException;
import com.ctps.ctps_api.global.security.CurrentUserContext;
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
    private final UserRepository userRepository;

    public List<StudySetResponse> getStudySets() {
        Long userId = CurrentUserContext.getRequired().getId();
        return studySetRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(StudySetResponse::from).toList();
    }

    public StudySetResponse getStudySet(Long id) {
        return StudySetResponse.from(findById(id));
    }

    @Transactional
    public StudySetResponse createStudySet(StudySetCreateRequest request) {
        User user = userRepository.getReferenceById(CurrentUserContext.getRequired().getId());
        StudySet studySet = StudySet.builder()
                .user(user)
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
        Long userId = CurrentUserContext.getRequired().getId();
        return studySetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("공부 세트를 찾을 수 없습니다. id=" + id));
    }
}
