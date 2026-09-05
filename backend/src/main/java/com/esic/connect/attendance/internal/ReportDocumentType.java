package com.esic.connect.attendance.internal;

/**
 * Nature d'un document officiel (V34). Une seule valeur aujourd'hui :
 * l'attestation d'assiduité. Les autres restitutions (rapports, exports)
 * ne sont pas des documents opposables et n'ont pas d'identifiant à
 * vérifier.
 */
enum ReportDocumentType {
    ATTENDANCE_CERTIFICATE
}
