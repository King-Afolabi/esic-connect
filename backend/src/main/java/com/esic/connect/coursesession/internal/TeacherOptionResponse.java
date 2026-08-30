package com.esic.connect.coursesession.internal;

import com.esic.connect.identity.TeacherDirectory;

import java.util.UUID;

/**
 * Formateur éligible proposé au formulaire de création d'une séance
 * (identité minimale, jamais d'identifiant SQL ni d'adresse électronique).
 */
record TeacherOptionResponse(UUID publicId, String firstName, String lastName) {

    static TeacherOptionResponse from(TeacherDirectory.TeacherRef ref) {
        return new TeacherOptionResponse(ref.publicId(), ref.firstName(), ref.lastName());
    }
}
