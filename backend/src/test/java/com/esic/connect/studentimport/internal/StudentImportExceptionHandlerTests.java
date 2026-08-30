package com.esic.connect.studentimport.internal;

import com.esic.connect.shared.web.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapping {@link StudentImportException.Kind} → {@link ApiError} (code
 * HTTP + code {@code IMP_*} + détails non sensibles), et retraduction du
 * dépassement de taille multipart. Aucun message ne contient de donnée
 * personnelle.
 */
class StudentImportExceptionHandlerTests {

    private final StudentImportExceptionHandler handler = new StudentImportExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/student-imports");

    @Test
    void mapsEveryKindToAStableHttpStatusAndCode() {
        assertMapping(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "IMP_UNSUPPORTED_MEDIA_TYPE");
        assertMapping(StudentImportException.Kind.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "IMP_FILE_TOO_LARGE");
        assertMapping(StudentImportException.Kind.ENCODING_INVALID, HttpStatus.BAD_REQUEST, "IMP_ENCODING_INVALID");
        assertMapping(StudentImportException.Kind.MISSING_COLUMN, HttpStatus.BAD_REQUEST, "IMP_MISSING_COLUMN");
        assertMapping(StudentImportException.Kind.TOO_MANY_ROWS, HttpStatus.BAD_REQUEST, "IMP_TOO_MANY_ROWS");
        assertMapping(StudentImportException.Kind.NO_DATA_ROWS, HttpStatus.BAD_REQUEST, "IMP_NO_DATA_ROWS");
        assertMapping(StudentImportException.Kind.HEADER_UNREADABLE, HttpStatus.BAD_REQUEST, "IMP_HEADER_UNREADABLE");
        assertMapping(StudentImportException.Kind.JOB_NOT_FOUND, HttpStatus.NOT_FOUND, "IMP_JOB_NOT_FOUND");
        assertMapping(StudentImportException.Kind.JOB_FORBIDDEN, HttpStatus.FORBIDDEN, "IMP_JOB_FORBIDDEN");
        assertMapping(StudentImportException.Kind.INVALID_SORT, HttpStatus.BAD_REQUEST, "IMP_INVALID_SORT");
        assertMapping(StudentImportException.Kind.INVALID_FILTER, HttpStatus.BAD_REQUEST, "IMP_INVALID_FILTER");
        assertMapping(StudentImportException.Kind.SCOPE_FORBIDDEN, HttpStatus.FORBIDDEN, "IMP_SCOPE_FORBIDDEN");
        assertMapping(StudentImportException.Kind.NOT_CONFIRMABLE, HttpStatus.CONFLICT, "IMP_NOT_CONFIRMABLE");
        assertMapping(StudentImportException.Kind.STALE_SIMULATION, HttpStatus.CONFLICT, "IMP_STALE_SIMULATION");
        assertMapping(StudentImportException.Kind.SIMULATION_EXPIRED, HttpStatus.CONFLICT, "IMP_SIMULATION_EXPIRED");
        assertMapping(StudentImportException.Kind.JOB_CANCELLED, HttpStatus.CONFLICT, "IMP_JOB_CANCELLED");
        assertMapping(StudentImportException.Kind.CONFIRM_FORBIDDEN, HttpStatus.FORBIDDEN, "IMP_CONFIRM_FORBIDDEN");
        assertMapping(StudentImportException.Kind.JOB_NOT_CANCELLABLE, HttpStatus.CONFLICT, "IMP_JOB_NOT_CANCELLABLE");
        assertMapping(StudentImportException.Kind.STUDENT_NUMBER_ALLOC_FAILED, HttpStatus.CONFLICT,
                "IMP_STUDENT_NUMBER_ALLOC_FAILED");
        assertMapping(StudentImportException.Kind.STUDENT_NUMBER_EXHAUSTED, HttpStatus.CONFLICT,
                "IMP_STUDENT_NUMBER_EXHAUSTED");
    }

    @Test
    void carriesTheNonSensitiveDetailIntoApiErrorDetails() {
        ResponseEntity<ApiError> response = handler.handle(
                new StudentImportException(StudentImportException.Kind.MISSING_COLUMN, List.of("email", "class_code")),
                request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).containsExactly("email", "class_code");
    }

    @Test
    void retranslatesMultipartOverflowToFileTooLarge() {
        ResponseEntity<ApiError> response =
                handler.handleMaxUpload(new MaxUploadSizeExceededException(2_097_152L), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("IMP_FILE_TOO_LARGE");
    }

    private void assertMapping(StudentImportException.Kind kind, HttpStatus expectedStatus, String expectedCode) {
        ResponseEntity<ApiError> response = handler.handle(new StudentImportException(kind), request);
        assertThat(response.getStatusCode()).as("status for %s", kind).isEqualTo(expectedStatus);
        assertThat(response.getBody()).as("body for %s", kind).isNotNull();
        assertThat(response.getBody().code()).as("code for %s", kind).isEqualTo(expectedCode);
        assertThat(response.getBody().message()).as("message for %s", kind).isNotBlank();
    }
}
