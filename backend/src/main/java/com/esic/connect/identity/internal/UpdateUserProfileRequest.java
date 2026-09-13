package com.esic.connect.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corps de {@code PATCH /users/{publicId}}. Le numéro étudiant n'en fait
 * jamais partie : immuable une fois posé (voir
 * {@link UserAccount#assignStudentNumber}), il ne se corrige que par le
 * réimport CSV ({@code studentimport}).
 */
record UpdateUserProfileRequest(
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 30) String phone,
        LocalDate birthDate) {
}
