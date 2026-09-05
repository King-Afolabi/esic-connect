package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface ProgramRepository extends JpaRepository<Program, Long>, JpaSpecificationExecutor<Program> {

    Optional<Program> findByPublicId(UUID publicId);

    Optional<Program> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);

    /**
     * Recherche globale (EF-USER-009) : code ou nom contenant le fragment.
     *
     * <p>{@code LIKE '%…%'} n'utilise pas d'index — c'est assumé : le
     * référentiel des formations d'un établissement se compte en dizaines,
     * et un index plein texte serait une complexité sans contrepartie
     * mesurable à cette volumétrie. Le résultat est borné par l'appelant.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM Program p
            WHERE LOWER(p.code) LIKE :pattern OR LOWER(p.name) LIKE :pattern
            ORDER BY p.code ASC
            """)
    java.util.List<Program> search(@org.springframework.data.repository.query.Param("pattern") String pattern,
                                   org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM Program p
            WHERE p.id IN :ids AND (LOWER(p.code) LIKE :pattern OR LOWER(p.name) LIKE :pattern)
            ORDER BY p.code ASC
            """)
    java.util.List<Program> searchWithin(@org.springframework.data.repository.query.Param("pattern") String pattern,
                                         @org.springframework.data.repository.query.Param("ids")
                                         java.util.Collection<Long> ids,
                                         org.springframework.data.domain.Pageable pageable);
}
