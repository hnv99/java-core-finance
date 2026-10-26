package com.fincore.common.repository;

import com.fincore.common.Identifiable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation shared by all entity repositories.
 * Subclasses add entity-specific queries.
 */
public class InMemoryRepository<T extends Identifiable<ID>, ID> implements Repository<T, ID> {

    protected final Map<ID, T> store = new ConcurrentHashMap<>();

    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<T> findAll() {
        // Defensive copy: callers cannot modify the internal storage.
        return List.copyOf(store.values());
    }

    @Override
    public boolean existsById(ID id) {
        return store.containsKey(id);
    }

    @Override
    public void deleteById(ID id) {
        store.remove(id);
    }

    @Override
    public int count() {
        return store.size();
    }
}
