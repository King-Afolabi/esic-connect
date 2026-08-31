package com.esic.connect.studentimport.internal;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde réflexive (rapport §14.4, invariants T2 / T5) : les méthodes
 * d'<em>application</em> des ports d'import portent {@code @Transactional}
 * <strong>sans</strong> {@code propagation = REQUIRES_NEW} — elles doivent
 * rejoindre la transaction unique de la confirmation, jamais en ouvrir
 * une autonome. Même approche que
 * {@code DefaultDemoAccountProvisionerTests} qui vérifie {@code @Profile}.
 */
class StudentImportProvisionerContractTests {

    @Test
    void identityProvisionerWriteMethodsUseRequiredPropagation() throws Exception {
        Class<?> impl = Class.forName("com.esic.connect.identity.internal.DefaultStudentAccountProvisioner");
        assertRequired(impl, "prepareStudentAccountAndInvitation",
                Class.forName("com.esic.connect.identity.StudentAccountProvisioner$NewStudentAccount"), Long.class);
        assertRequired(impl, "updateStudentPhone", java.util.UUID.class, String.class, Long.class);
    }

    @Test
    void enrollmentProvisionerWriteMethodsUseRequiredPropagation() throws Exception {
        Class<?> impl = Class.forName("com.esic.connect.enrollment.internal.DefaultStudentEnrollmentProvisioner");
        Class<?> command = Class.forName("com.esic.connect.enrollment.StudentEnrollmentProvisioner$ProvisionProfile");
        assertRequired(impl, "provisionProfile", command);
        assertRequired(impl, "provisionEnrollment", java.util.UUID.class, java.util.UUID.class,
                java.time.LocalDate.class, Long.class);
        assertRequired(impl, "provisionTransfer", java.util.UUID.class, java.util.UUID.class,
                java.time.LocalDate.class, String.class, Long.class);
        assertRequired(impl, "updateProfileAlternation", java.util.UUID.class, boolean.class, String.class, Long.class);
    }

    @Test
    void noWriteMethodDeclaresRequiresNew() throws Exception {
        for (String className : List.of(
                "com.esic.connect.identity.internal.DefaultStudentAccountProvisioner",
                "com.esic.connect.enrollment.internal.DefaultStudentEnrollmentProvisioner")) {
            for (Method method : Class.forName(className).getDeclaredMethods()) {
                Transactional tx = method.getAnnotation(Transactional.class);
                if (tx != null) {
                    assertThat(tx.propagation())
                            .as("%s.%s ne doit jamais ouvrir une transaction autonome",
                                    className, method.getName())
                            .isNotEqualTo(Propagation.REQUIRES_NEW);
                }
            }
        }
    }

    private static void assertRequired(Class<?> impl, String methodName, Class<?>... paramTypes) throws Exception {
        Method method = impl.getDeclaredMethod(methodName, paramTypes);
        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).as("%s.%s doit porter @Transactional", impl.getSimpleName(), methodName).isNotNull();
        assertThat(tx.propagation())
                .as("%s.%s doit rejoindre la transaction de l'appelant (REQUIRED)", impl.getSimpleName(), methodName)
                .isEqualTo(Propagation.REQUIRED);
    }
}
