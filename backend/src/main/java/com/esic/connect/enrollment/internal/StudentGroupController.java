package com.esic.connect.enrollment.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Groupes temporaires d'apprenants (EF-ACA-007 ; docs/02 §6.5).
 *
 * <p>Le {@code @PreAuthorize} filtre par rôle ; le périmètre pédagogique
 * est décidé <strong>dans le service</strong>, à partir du contexte de
 * sécurité — jamais d'un paramètre fourni par l'appelant. Un
 * {@code PEDAGOGICAL_MANAGER} ne voit et ne modifie que les groupes de
 * ses formations.
 */
@RestController
@RequestMapping("/api/v1/student-groups")
class StudentGroupController {

    /** Gestion des groupes : rôles administratifs et responsable pédagogique. */
    private static final String GROUP_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";

    private final StudentGroupService service;

    StudentGroupController(StudentGroupService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(GROUP_ROLES)
    PageResponse<StudentGroupResponse> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String programId,
                                            @RequestParam(required = false) String sort,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, q, programId, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(GROUP_ROLES)
    StudentGroupResponse get(@PathVariable String publicId) {
        return service.get(parse(publicId));
    }

    @PostMapping
    @PreAuthorize(GROUP_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    StudentGroupResponse create(@Valid @RequestBody StudentGroupRequests.Create request,
                                @AuthenticationPrincipal Jwt caller) {
        return service.create(request, EnrollmentWeb.subject(caller));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize(GROUP_ROLES)
    StudentGroupResponse update(@PathVariable String publicId,
                                @Valid @RequestBody StudentGroupRequests.Update request,
                                @AuthenticationPrincipal Jwt caller) {
        return service.update(parse(publicId), request, EnrollmentWeb.subject(caller));
    }

    @PostMapping("/{publicId}/archive")
    @PreAuthorize(GROUP_ROLES)
    StudentGroupResponse archive(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return service.archive(parse(publicId), EnrollmentWeb.subject(caller));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize(GROUP_ROLES)
    StudentGroupResponse restore(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        return service.restore(parse(publicId), EnrollmentWeb.subject(caller));
    }

    @GetMapping("/{publicId}/members")
    @PreAuthorize(GROUP_ROLES)
    List<StudentGroupResponse.Member> members(@PathVariable String publicId) {
        return service.members(parse(publicId));
    }

    @PostMapping("/{publicId}/members")
    @PreAuthorize(GROUP_ROLES)
    List<StudentGroupResponse.Member> addMembers(@PathVariable String publicId,
                                                 @Valid @RequestBody StudentGroupRequests.AddMembers request,
                                                 @AuthenticationPrincipal Jwt caller) {
        return service.addMembers(parse(publicId), request, EnrollmentWeb.subject(caller));
    }

    @DeleteMapping("/{publicId}/members/{memberId}")
    @PreAuthorize(GROUP_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable String publicId, @PathVariable String memberId,
                      @AuthenticationPrincipal Jwt caller) {
        service.removeMember(parse(publicId), parse(memberId), EnrollmentWeb.subject(caller));
    }

    private static java.util.UUID parse(String publicId) {
        return EnrollmentWeb.parseUuid(publicId, EnrollmentException.Kind.STUDENT_GROUP_NOT_FOUND);
    }
}
