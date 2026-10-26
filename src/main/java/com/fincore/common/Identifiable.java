package com.fincore.common;

/**
 * An entity with a unique identity. Two entities are the same if their ids are equal,
 * even when other attributes differ.
 *
 * @param <ID> type of the identifier
 */
public interface Identifiable<ID> {

    ID getId();
}
