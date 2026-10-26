package com.fincore.common.repository;

import com.fincore.common.Identifiable;

import java.util.List;
import java.util.Optional;

/**
 * Generic persistence abstraction. Services depend on this interface, not on the
 * storage technology, so an in-memory map could later be replaced by JDBC.
 *
 * @param <T>  entity type (bounded: it must expose an id)
 * @param <ID> identifier type
 */
public interface Repository<T extends Identifiable<ID>, ID> {

    T save(T entity);

    /** Returns {@link Optional#empty()} instead of {@code null} when nothing is found. */
    Optional<T> findById(ID id);

    List<T> findAll();

    boolean existsById(ID id);

    void deleteById(ID id);

    int count();
}
